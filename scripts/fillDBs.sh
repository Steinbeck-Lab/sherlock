curl -X POST -i 'http://localhost:8081/sherlock-db-service-dataset/replaceAll?nuclei=13C,1H&setLimits=true'
curl -X POST -i 'http://localhost:8081/sherlock-db-service-statistics/hybridization/replaceAll?nuclei=13C'
curl -X POST -i 'http://localhost:8081/sherlock-db-service-statistics/connectivity/replaceAll?nuclei=13C'
curl -X POST -i 'http://localhost:8081/sherlock-db-service-hosecode/replaceAll?nuclei=13C&maxSphere=6'

# to create the HOSE code map in shared volume
# curl -X GET -i 'http://localhost:8081/sherlock-db-service-hosecode/saveAllAsMap'
# before publishing copy the HOSE code map from shared volume into local data folder
# docker cp sherlock-pylsd:/data/hosecode/hosecodes.json backend/sherlock-pylsd/data/hosecode/