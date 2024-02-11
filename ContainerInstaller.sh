docker pull axill12/applications_of_datacenter:server$1
docker run --name server$1_container -p $((6833 + $1)):$((6833 + $1)) -d axill12/applications_of_datacenter:server$1