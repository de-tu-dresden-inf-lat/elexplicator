#!/bin/sh
set -x

echo "Evaluating Not Sure Options"

reRuns=0;

total=10;
current=0;

p='elexplicator'
u='service'

more=0;

checkpoint = 'OptionsEval/programState.json'


examples_dir='/home/service/Desktop/Examples2'
intermediate_out_dir='/home/service/Desktop/Examples2'

echo "Watching out for -> process = "$p", user = "$u
if ! id -u $u > /dev/null;
then
	more=2;
fi

#CLASS_PATH="target/classes"
JAR_PATH="target/ELExplicator.jar"

CLASS_NAME="de.tu_dresden.lat.evaluate.RunEvaluation2"

status_value="Failure";

while [ $more -eq 0];
do 
	if [$current -eq 0];
	then 
		current=$total;
		if ! pgrep -u $u -x $p > /dev/null;
		then 
			echo "Run number: "$reRuns
			if [ $reRuns -gt 0 ];
			then 
				status_value=$(grep -oP '"status":*"\K[^"]+' "$checkpoint");
				if [[ $status_value == "Success" ]];
				then 
					echo "Process completed successfully. Exiting.";
					more=1;
					exit 1;
				fi
			fi
			if [ $reRuns -eq 0 ];
			then 
				java -cp "$JAR_PATH" "$CLASS_NAME" "$examples_dir" "$intermediate_out_dir" "False" -XX:+ExitOnOutOfMemoryError
			else
				java -cp "$JAR_PATH" "$CLASS_NAME" "$examples_dir" "$intermediate_out_dir" "True" -XX:+ExitOnOutOfMemoryError 
			fi
			let $reRuns=$reRuns+1;
			sleep 5;
		fi
	else
		let $current=$current-1;
		sleep 5;
	fi
done



            
