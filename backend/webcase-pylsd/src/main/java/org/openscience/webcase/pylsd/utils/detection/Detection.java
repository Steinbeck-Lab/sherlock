package org.openscience.webcase.pylsd.utils.detection;

import casekit.nmr.model.nmrium.Correlation;
import org.openscience.webcase.pylsd.model.Detections;
import org.openscience.webcase.pylsd.model.exchange.Transfer;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Detection {

    public static Transfer detect(final WebClient.Builder webClientBuilder, final Transfer requestTransfer) {
        final Transfer responseTransfer = new Transfer();
        final int shiftTol = 0;
        final List<Correlation> correlationList = requestTransfer.getData()
                                                                 .getCorrelations()
                                                                 .getValues();
        final Map<Integer, List<Integer>> detectedHybridizations = HybridizationDetection.detectHybridizations(
                webClientBuilder, correlationList, requestTransfer.getDetectionOptions()
                                                                  .getHybridizationDetectionThreshold(), shiftTol);
        System.out.println("detectedHybridizations: "
                                   + detectedHybridizations);
        // set hybridization of correlations from detection if there was nothing set before
        for (final Map.Entry<Integer, List<Integer>> entry : detectedHybridizations.entrySet()) {
            if (correlationList.get(entry.getKey())
                               .getHybridization()
                    == null
                    || correlationList.get(entry.getKey())
                                      .getHybridization()
                                      .isEmpty()) {
                correlationList.get(entry.getKey())
                               .setHybridization(new ArrayList<>(entry.getValue()));
            }
        }

        final Map<Integer, Map<String, Map<Integer, Set<Integer>>>> detectedConnectivities = ConnectivityDetection.detectConnectivities(
                webClientBuilder, correlationList, shiftTol, requestTransfer.getDetectionOptions()
                                                                            .getLowerElementCountThreshold(),
                requestTransfer.getMf());

        System.out.println("detectedConnectivities: "
                                   + detectedConnectivities);

        final Map<Integer, Map<String, Map<Integer, Set<Integer>>>> forbiddenNeighbors = ForbiddenNeighborDetection.detectForbiddenNeighbors(
                detectedConnectivities, requestTransfer.getMf());
        System.out.println("-> forbiddenNeighbors: "
                                   + forbiddenNeighbors);

        final Map<Integer, Map<String, Map<Integer, Set<Integer>>>> setNeighbors = ConnectivityDetection.detectConnectivities(
                webClientBuilder, correlationList, shiftTol, requestTransfer.getDetectionOptions()
                                                                            .getUpperElementCountThreshold(),
                requestTransfer.getMf());
        System.out.println("-> setNeighbors: "
                                   + setNeighbors);

        responseTransfer.setDetections(
                new Detections(detectedHybridizations, detectedConnectivities, forbiddenNeighbors, setNeighbors));

        return responseTransfer;
    }
}
