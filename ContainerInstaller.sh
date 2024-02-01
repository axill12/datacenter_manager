docker pull axill12/applications_of_datacenter:server$1
docker run --name server$1_container -p 683$(($1 + 3)):683$(($1 + 3)) -d axill12/applications_of_datacenter:server$1