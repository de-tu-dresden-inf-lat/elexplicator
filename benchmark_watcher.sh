#!/bin/sh

echo "Benchmarking Ontologies"

reRuns=0;

total=10;
current=0;

p='elexplicator'
u='service'

more=0;

logger_file='Benchmark/benchmark_log.json'
benchmark_instances_dir='benchmark_instances'

echo "Watching out for -> process = "$p", user = "$u
if ! id -u $u > /dev/null;
then
	more=2;
fi

CLASS_PATH="target/classes"
JAR_PATH="target/ELExplicator.jar"

CLASS_NAME="de.tu_dresden.benchmarking.BenchmarkOntologies"

status_value="Failure";

while [ $more -eq 0 ];
do
    if [ $current -eq 0 ];
    then
        current=$total;
        if ! pgrep -u $u -x $p > /dev/null;
        then
            echo "Run number "$reRuns;
            if [ $reRuns -gt 0 ];
            then
                status_value=$(grep -oP '"Status": *"\K[^"]+' "$logger_file")
                if [[ "$status_value" = "Success" ]];
                then    
                    echo "Already succeeded!";
                    more=1;
                    exit 1;
                fi
            fi
            if [ $reRuns -eq 0 ];
            then
                java -Xms2G -Xmx8G -cp  "$CLASS_PATH:$JAR_PATH" "$CLASS_NAME" "True" -XX:+ExitOnOutOfMemoryError
            else    
                java -Xms2G -Xmx8G -cp -Xms2G -Xmx8G "$CLASS_PATH:$JAR_PATH" "$CLASS_NAME" "False" -XX:+ExitOnOutOfMemoryError
            fi
            let reRuns=$reRuns+1;
            sleep 5;
        fi
    else
        let current=$current-1;
    fi
done