docker stop server$1_container > deleter.txt
docker rm server$1_container >> deleter.txt
docker rmi axill12/applications_of_datacenter:server$1 >> deleter.txt