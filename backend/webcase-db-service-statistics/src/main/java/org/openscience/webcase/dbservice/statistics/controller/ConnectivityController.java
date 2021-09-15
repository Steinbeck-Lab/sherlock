package org.openscience.webcase.dbservice.statistics.controller;

import casekit.nmr.analysis.ConnectivityStatistics;
import casekit.nmr.utils.Utils;
import org.openscience.webcase.dbservice.statistics.service.ConnectivityServiceImplementation;
import org.openscience.webcase.dbservice.statistics.service.model.ConnectivityRecord;
import org.openscience.webcase.dbservice.statistics.service.model.DataSetRecord;
import org.openscience.webcase.dbservice.statistics.utils.Utilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/connectivity")
public class ConnectivityController {

    private final WebClient.Builder webClientBuilder;
    private final ConnectivityServiceImplementation connectivityServiceImplementation;

    @Autowired
    public ConnectivityController(final WebClient.Builder webClientBuilder,
                                  final ConnectivityServiceImplementation connectivityServiceImplementation) {
        this.webClientBuilder = webClientBuilder;
        this.connectivityServiceImplementation = connectivityServiceImplementation;
    }

    @GetMapping(value = "/count", produces = "application/json")
    public Mono<Long> getCount() {
        return this.connectivityServiceImplementation.count();
    }

    @GetMapping(value = "/getAll", produces = "application/stream+json")
    public Flux<ConnectivityRecord> getAll() {
        return this.connectivityServiceImplementation.findAll();
    }

    @GetMapping(value = "/getConnectivityCounts", produces = "application/stream+json")
    public Flux<ConnectivityRecord> findByNucleusAndHybridizationAndMultiplicityAndShift(
            @RequestParam final String nucleus, @RequestParam final String hybridization,
            @RequestParam final String multiplicity, @RequestParam final int minShift,
            @RequestParam final int maxShift) {
        return this.connectivityServiceImplementation.findByNucleusAndHybridizationAndMultiplicityAndShift(nucleus,
                                                                                                           hybridization,
                                                                                                           multiplicity,
                                                                                                           minShift,
                                                                                                           maxShift);
    }

    @PostMapping(value = "/deleteAll")
    public Mono<Void> deleteAll() {
        return this.connectivityServiceImplementation.deleteAll();
    }

    @PostMapping(value = "/replaceAll")
    public void replaceAll(@RequestParam final String[] nuclei) {
        this.deleteAll()
            .block();

        // nucleus -> multiplicity -> hybridization -> shift (int) -> connected atom symbol -> connected atom hybridization -> connected atom protons count -> occurrence
        final Map<String, Map<String, Map<String, Map<Integer, Map<String, Map<String, Map<Integer, Integer>>>>>>> connectivityStatisticsPerNucleus = new ConcurrentHashMap<>();
        Utilities.getByDataSetSpectrumNuclei(this.webClientBuilder, nuclei)
                 .map(DataSetRecord::getDataSet)
                 .doOnNext(dataSet -> {
                     final String nucleus = dataSet.getSpectrum()
                                                   .getNuclei()[0];
                     final String atomType = Utils.getAtomTypeFromNucleus(nucleus);
                     connectivityStatisticsPerNucleus.putIfAbsent(nucleus, new ConcurrentHashMap<>());
                     ConnectivityStatistics.buildConnectivityStatistics(dataSet, atomType,
                                                                        connectivityStatisticsPerNucleus.get(nucleus));
                 })
                 .doAfterTerminate(() -> {
                     System.out.println(" -> connectivityStatistics done");
                     connectivityStatisticsPerNucleus.keySet()
                                                     .forEach(nucleus -> connectivityStatisticsPerNucleus.get(nucleus)
                                                                                                         .keySet()
                                                                                                         .forEach(
                                                                                                                 multiplicity -> connectivityStatisticsPerNucleus.get(
                                                                                                                         nucleus)
                                                                                                                                                                 .get(multiplicity)
                                                                                                                                                                 .keySet()
                                                                                                                                                                 .forEach(
                                                                                                                                                                         hybridization -> connectivityStatisticsPerNucleus.get(
                                                                                                                                                                                 nucleus)
                                                                                                                                                                                                                          .get(multiplicity)
                                                                                                                                                                                                                          .get(hybridization)
                                                                                                                                                                                                                          .keySet()
                                                                                                                                                                                                                          .forEach(
                                                                                                                                                                                                                                  shift -> {
                                                                                                                                                                                                                                      this.connectivityServiceImplementation.insert(
                                                                                                                                                                                                                                              new ConnectivityRecord(
                                                                                                                                                                                                                                                      null,
                                                                                                                                                                                                                                                      nucleus,
                                                                                                                                                                                                                                                      hybridization,
                                                                                                                                                                                                                                                      multiplicity,
                                                                                                                                                                                                                                                      shift,
                                                                                                                                                                                                                                                      connectivityStatisticsPerNucleus.get(
                                                                                                                                                                                                                                                              nucleus)
                                                                                                                                                                                                                                                                                      .get(multiplicity)
                                                                                                                                                                                                                                                                                      .get(hybridization)
                                                                                                                                                                                                                                                                                      .get(shift)))
                                                                                                                                                                                                                                                                            .subscribe();
                                                                                                                                                                                                                                  }))));
                     System.out.println(" -> done -> "
                                                + this.getCount()
                                                      .block());
                 })
                 .subscribe();
    }

    @GetMapping(value = "/detectConnectivities", produces = "application/json")
    public Map<String, Map<String, Map<Integer, Integer>>> detectConnectivities(@RequestParam final String nucleus,
                                                                                @RequestParam final String hybridization,
                                                                                @RequestParam final String multiplicity,
                                                                                @RequestParam final int minShift,
                                                                                @RequestParam final int maxShift,
                                                                                @RequestParam final double thresholdHybridizationCount,
                                                                                @RequestParam final double thresholdProtonsCount,
                                                                                @RequestParam final String mf) {
        final List<Map<String, Map<String, Map<Integer, Integer>>>> detectedConnectivities = this.findByNucleusAndHybridizationAndMultiplicityAndShift(
                nucleus, hybridization, multiplicity, minShift, maxShift)
                                                                                                 .map(ConnectivityRecord::getConnectivityCounts)
                                                                                                 .collectList()
                                                                                                 .block();

        final Set<String> atomTypesByMf = Utils.getMolecularFormulaElementCounts(mf)
                                               .keySet();
        detectedConnectivities.forEach(foundExtractedConnectivityCountsMap -> {
            final Set<String> foundAtomTypesToIgnore = foundExtractedConnectivityCountsMap.keySet()
                                                                                          .stream()
                                                                                          .filter(foundAtomType -> !atomTypesByMf.contains(
                                                                                                  foundAtomType))
                                                                                          .collect(Collectors.toSet());
            for (final String foundAtomTypeToIgnore : foundAtomTypesToIgnore) {
                foundExtractedConnectivityCountsMap.remove(foundAtomTypeToIgnore);
            }
        });

        // atom type neighbor -> hybridization -> protons count -> occurrence
        final Map<String, Map<String, Map<Integer, Integer>>> filteredExtractedConnectivitiesAll = new HashMap<>();
        // loop over all results from DB in case a chemical shift range is given (minShift != maxShift)
        for (final Map<String, Map<String, Map<Integer, Integer>>> extractedConnectivityCount : detectedConnectivities) {
            final Map<String, Map<String, Map<Integer, Integer>>> filteredExtractedConnectivities = ConnectivityStatistics.filterExtractedConnectivities(
                    extractedConnectivityCount, thresholdHybridizationCount, thresholdProtonsCount);
            for (final String extractedNeighborAtomType : filteredExtractedConnectivities.keySet()) {
                filteredExtractedConnectivitiesAll.putIfAbsent(extractedNeighborAtomType, new HashMap<>());
                filteredExtractedConnectivities.get(extractedNeighborAtomType)
                                               .keySet()
                                               .forEach(hybridizationNeighbor -> {
                                                   filteredExtractedConnectivitiesAll.get(extractedNeighborAtomType)
                                                                                     .putIfAbsent(hybridizationNeighbor,
                                                                                                  new HashMap<>());

                                                   filteredExtractedConnectivities.get(extractedNeighborAtomType)
                                                                                  .get(hybridizationNeighbor)
                                                                                  .keySet()
                                                                                  .forEach(protonsCount -> {
                                                                                      filteredExtractedConnectivitiesAll.get(
                                                                                              extractedNeighborAtomType)
                                                                                                                        .get(hybridizationNeighbor)
                                                                                                                        .putIfAbsent(
                                                                                                                                protonsCount,
                                                                                                                                0);
                                                                                      filteredExtractedConnectivitiesAll.get(
                                                                                              extractedNeighborAtomType)
                                                                                                                        .get(hybridizationNeighbor)
                                                                                                                        .put(protonsCount,
                                                                                                                             filteredExtractedConnectivitiesAll.get(
                                                                                                                                     extractedNeighborAtomType)
                                                                                                                                                               .get(hybridizationNeighbor)
                                                                                                                                                               .get(protonsCount)
                                                                                                                                     + filteredExtractedConnectivities.get(
                                                                                                                                     extractedNeighborAtomType)
                                                                                                                                                                      .get(hybridizationNeighbor)
                                                                                                                                                                      .get(protonsCount));
                                                                                  });

                                               });
            }
        }

        return filteredExtractedConnectivitiesAll;
    }
}