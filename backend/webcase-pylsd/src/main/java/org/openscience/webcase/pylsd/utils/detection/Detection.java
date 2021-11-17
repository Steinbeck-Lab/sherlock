package org.openscience.webcase.pylsd.utils.detection;

import casekit.nmr.lsd.Constants;
import casekit.nmr.model.nmrium.Correlation;
import org.openscience.webcase.pylsd.model.Detections;
import org.openscience.webcase.pylsd.model.exchange.Transfer;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

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
        // set hybridization of correlations from detection
        for (final Map.Entry<Integer, List<Integer>> entry : detectedHybridizations.entrySet()) {
            correlationList.get(entry.getKey())
                           .setHybridization(entry.getValue());
        }

        final Map<Integer, Map<String, Map<Integer, Set<Integer>>>> detectedConnectivities = ConnectivityDetection.detectConnectivities(
                webClientBuilder, correlationList, shiftTol, requestTransfer.getDetectionOptions()
                                                                            .getLowerElementCountThreshold(),
                requestTransfer.getMf());

        System.out.println("detectedConnectivities: "
                                   + detectedConnectivities);

        final Map<Integer, Map<String, Map<Integer, Set<Integer>>>> forbiddenNeighbors = ForbiddenNeighborDetection.detectForbiddenNeighbors(
                detectedConnectivities, requestTransfer.getMf());
        reduce(forbiddenNeighbors);
        System.out.println("-> forbiddenNeighbors: "
                                   + forbiddenNeighbors);

        final Map<Integer, Map<String, Map<Integer, Set<Integer>>>> setNeighbors = ConnectivityDetection.detectConnectivities(
                webClientBuilder, correlationList, shiftTol, requestTransfer.getDetectionOptions()
                                                                            .getUpperElementCountThreshold(),
                requestTransfer.getMf());
        reduce(setNeighbors);
        System.out.println("-> setNeighbors: "
                                   + setNeighbors);

        responseTransfer.setData(requestTransfer.getData());
        responseTransfer.setDetections(
                new Detections(detectedHybridizations, detectedConnectivities, forbiddenNeighbors, setNeighbors));

        return responseTransfer;
    }


    private static void reduce(final Map<Integer, Map<String, Map<Integer, Set<Integer>>>> neighbors) {
        for (final Map.Entry<Integer, Map<String, Map<Integer, Set<Integer>>>> entryPerCorrelation : neighbors.entrySet()) {
            for (final Map.Entry<String, Map<Integer, Set<Integer>>> entryPerAtomType : entryPerCorrelation.getValue()
                                                                                                           .entrySet()) {
                final Set<Integer> defaultHybridizations = Arrays.stream(
                                                                         Constants.defaultHybridizationMap.get(entryPerAtomType.getKey()))
                                                                 .boxed()
                                                                 .collect(Collectors.toSet());
                if (entryPerAtomType.getValue()
                                    .keySet()
                                    .containsAll(defaultHybridizations)) {
                    final Set<Integer> defaultProtonsCounts = Arrays.stream(
                                                                            Constants.defaultProtonsCountPerValencyMap.get(entryPerAtomType.getKey()))
                                                                    .boxed()
                                                                    .collect(Collectors.toSet());
                    for (final int protonsCount : defaultProtonsCounts) {
                        boolean foundInAllHybridizations = true;
                        for (final Map.Entry<Integer, Set<Integer>> entryPerHybridization : entryPerAtomType.getValue()
                                                                                                            .entrySet()) {
                            if (entryPerHybridization.getKey()
                                    != -1
                                    && !entryPerHybridization.getValue()
                                                             .contains(protonsCount)) {
                                foundInAllHybridizations = false;
                                break;
                            }
                        }
                        if (foundInAllHybridizations) {
                            // remove protonsCount from hybridization
                            for (final Map.Entry<Integer, Set<Integer>> entryPerHybridization : entryPerAtomType.getValue()
                                                                                                                .entrySet()) {
                                if (entryPerHybridization.getKey()
                                        != -1) {
                                    entryPerHybridization.getValue()
                                                         .remove(protonsCount);
                                }
                            }
                            // add protonsCount to -1 which means all hybridization states
                            entryPerAtomType.getValue()
                                            .putIfAbsent(-1, new HashSet<>());
                            entryPerAtomType.getValue()
                                            .get(-1)
                                            .add(protonsCount);
                        }
                    }
                }
                final Set<Integer> hybridizationsToRemove = new HashSet<>();
                for (final Map.Entry<Integer, Set<Integer>> entryPerHybridization : entryPerAtomType.getValue()
                                                                                                    .entrySet()) {
                    if (entryPerHybridization.getValue()
                                             .isEmpty()) {
                        hybridizationsToRemove.add(entryPerHybridization.getKey());
                    }
                }
                for (final int hybrid : hybridizationsToRemove) {
                    entryPerAtomType.getValue()
                                    .remove(hybrid);
                }
            }
        }
    }
}
