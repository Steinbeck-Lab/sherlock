package org.openscience.webcase.pylsd.controller;

import casekit.io.FileSystem;
import casekit.nmr.model.DataSet;
import org.openscience.webcase.pylsd.model.exchange.Transfer;
import org.openscience.webcase.pylsd.utils.InputFileBuilder;
import org.openscience.webcase.pylsd.utils.ParserAndPrediction;
import org.openscience.webcase.pylsd.utils.detection.Detection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/")
public class PyLSDController {
    final String pathToPyLSDExecutableFolder = "/data/lsd/PyLSD/Variant/";
    final String pathToPyLSDInputFileFolder = "/data/lsd/PyLSD/Variant/";
    final String pathToPyLSDResultFileFolder = "/data/lsd/PyLSD/Variant/";

    private final WebClient.Builder webClientBuilder;
    private final ExchangeStrategies exchangeStrategies;
    private final ParserAndPrediction parserAndPrediction;

    @Autowired
    public PyLSDController(final WebClient.Builder webClientBuilder, final ExchangeStrategies exchangeStrategies) {
        this.webClientBuilder = webClientBuilder;
        this.exchangeStrategies = exchangeStrategies;
        this.parserAndPrediction = new ParserAndPrediction(this.webClientBuilder, this.exchangeStrategies);
    }


    @PostMapping(value = "/runPyLSD")
    public ResponseEntity<Transfer> runPyLSD(@RequestBody final Transfer requestTransfer) {
        final Transfer responseTransfer = new Transfer();

        // build PyLSD input file
        final String pyLSDInputFileContent = InputFileBuilder.createPyLSDInputFile(this.webClientBuilder,
                                                                                   requestTransfer);
        System.out.println("file content:\n"
                                   + pyLSDInputFileContent);
        final String pathToPyLSDInputFile = this.pathToPyLSDInputFileFolder
                + requestTransfer.getRequestID()
                + ".pylsd";

        // run PyLSD if file was written successfully
        if (FileSystem.writeFile(pathToPyLSDInputFile, pyLSDInputFileContent)) {
            System.out.println("--> has been written successfully: "
                                       + pathToPyLSDInputFile);
            try {
                // try to execute PyLSD
                final ProcessBuilder builder = new ProcessBuilder();
                builder.directory(new File(this.pathToPyLSDExecutableFolder))
                       .redirectError(new File(this.pathToPyLSDInputFileFolder
                                                       + requestTransfer.getRequestID()
                                                       + "_error.txt"))
                       .redirectOutput(new File(this.pathToPyLSDInputFileFolder
                                                        + requestTransfer.getRequestID()
                                                        + "_log.txt"))
                       .command("python2.7", this.pathToPyLSDExecutableFolder
                               + "lsd.py", pathToPyLSDInputFile);
                final Process process = builder.start();
                final boolean pyLSDRunWasSuccessful = process.waitFor(requestTransfer.getElucidationOptions()
                                                                                     .getTimeLimitTotal(),
                                                                      TimeUnit.MINUTES);
                if (pyLSDRunWasSuccessful) {
                    System.out.println("-> run was successful");
                    final String pathToSmilesFile = this.pathToPyLSDResultFileFolder
                            + requestTransfer.getRequestID()
                            + "_0.smiles";
                    System.out.println(pathToSmilesFile);
                    requestTransfer.setPathToSmilesFile(pathToSmilesFile);

                    final ResponseEntity<Transfer> transferResponseEntity = this.parserAndPrediction.parseAndPredictFromSmilesFile(
                            requestTransfer);
                    if (transferResponseEntity.getStatusCode()
                                              .isError()) {
                        return transferResponseEntity;
                    }
                    final List<DataSet> dataSetList = transferResponseEntity.getBody()
                                                                            .getDataSetList();
                    System.out.println("--> number of parsed and ranked structures: "
                                               + dataSetList.size());

                    System.out.println("--> number of results: "
                                               + dataSetList.size());
                    responseTransfer.setDataSetList(dataSetList);
                } else {
                    System.out.println("run was NOT successful -> killing PyLSD run if it still exist");
                    this.cancel();
                }
            } catch (final Exception e) {
                e.printStackTrace();
                responseTransfer.setPyLSDRunWasSuccessful(false);
            }
            // cleanup of created files and folder
            final String[] directoriesToCheck = new String[]{this.pathToPyLSDInputFileFolder,
                                                             this.pathToPyLSDResultFileFolder};
            System.out.println("cleaned ? -> "
                                       + FileSystem.cleanup(directoriesToCheck, requestTransfer.getRequestID()));
        } else {
            System.out.println("--> input file creation failed at "
                                       + pathToPyLSDInputFile);
            responseTransfer.setErrorMessage("PyLSD input file creation failed at "
                                                     + pathToPyLSDInputFile);
            return new ResponseEntity<>(responseTransfer, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(responseTransfer, HttpStatus.OK);
    }

    @PostMapping(value = "/detect")
    public ResponseEntity<Transfer> detection(@RequestBody final Transfer requestTransfer) {
        return new ResponseEntity<>(Detection.detect(this.webClientBuilder, requestTransfer), HttpStatus.OK);
    }

    @GetMapping(value = "/cancel")
    public ResponseEntity<Transfer> cancel() {
        final Transfer responseTransfer = new Transfer();

        while (ProcessHandle.allProcesses()
                            .anyMatch(processHandle -> processHandle.isAlive()
                                    && processHandle.info()
                                                    .command()
                                                    .orElse("unknown")
                                                    .contains("LSD/lsd"))) {
            ProcessHandle.allProcesses()
                         .filter(processHandle -> processHandle.isAlive()
                                 && processHandle.info()
                                                 .command()
                                                 .orElse("unknown")
                                                 .contains("LSD/lsd"))
                         .findFirst()
                         .ifPresent(processHandleLSD -> {
                             System.out.println("-> killing PID "
                                                        + processHandleLSD.pid()
                                                        + ": "
                                                        + processHandleLSD.info()
                                                                          .command()
                                                                          .orElse("unknown"));
                             processHandleLSD.destroy();

                             while (processHandleLSD.isAlive()) {
                                 try {
                                     TimeUnit.SECONDS.sleep(1);
                                 } catch (final InterruptedException e) {
                                     e.printStackTrace();
                                     responseTransfer.setErrorMessage(e.getMessage());
                                 }
                             }
                         });
            if (responseTransfer.getErrorMessage()
                    != null) {
                return new ResponseEntity<>(responseTransfer, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<>(responseTransfer, HttpStatus.OK);
    }
}
