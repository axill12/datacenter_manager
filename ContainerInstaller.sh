docker pull axill12/applications_of_datacenter:server2 > installer_log.txt
docker run --name server2_container -p 6835:6835 -d axill12/applications_of_datacenter:server2 >> installer_log.txt