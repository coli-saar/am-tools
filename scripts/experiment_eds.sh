#! /bin/bash

# ./scripts/experiment_eds.sh <gold_prefix> <directories_with_scores.zip>
#
# e.g.: ./scripts/experiment_eds.sh EMNLP20/EDS/dev/dev-gold EMNLP20/EDS/dev/mtl-bert/

# change these variables away from their default values by setting them explicitly,
# e.g. THREADS=10 ./scripts/experiment_eds.sh ...
CORPUS_BASE=${CORPUS_BASE:-.}
JAVA_OPTIONS=${JAVA_OPTIONS:--Xmx4G}
THREADS=${THREADS:-2}
LIMIT_ITEMS=${LIMIT_ITEMS:-0}

GOLD_PREFIX=$1


# prepwork

#rm -f /tmp/typecache.dat
HOSTNAME=`hostname`

for corpus in "${@:2}" # all except special arguments
do
    mkdir -p $corpus
    rm -f $corpus/eval.tsv
done	      


# parsing

for heuristic in ignore_aware static trivial
do
    for corpus in "${@:2}" # all except special arguments
    do
	# scores file to read from
	if [ -f $CORPUS_BASE/$corpus/serialized-scores.zip ]; then
	    SCORES_ARG="-S $CORPUS_BASE/$corpus/serialized-scores.zip"
	else
	    SCORES_ARG="-s $CORPUS_BASE/$corpus/scores.zip"
	fi

	# csv file to write to
	csv=${corpus//\//_}.csv

	output=$($JAVA_HOME/bin/java $JAVA_OPTIONS -cp build/libs/am-tools.jar de.saar.coli.amtools.astar.Astar --print-data $SCORES_ARG --typecache $CORPUS_BASE/$corpus/typecache.dat --limit-items $LIMIT_ITEMS --threads $THREADS --outside-estimator $heuristic --statistics $corpus/$heuristic.csv -o $corpus )

	last_line=${output##*$'\n'}
	amconll=$(echo "$last_line"|cut -f5)

	output_prefix="$CORPUS_BASE/$corpus/predictions_$heuristic"
	./scripts/evaluate_eds.sh $output_prefix $GOLD_PREFIX $amconll
	smatch=$(grep F-score $output_prefix.smatch.txt|awk -F" " '{print $NF}')   # awk takes last field; cut can't do that
	edm=$(grep F1-score $output_prefix.edm.txt|head -1|cut -f2 -d " ")
	
	echo -e "$corpus\t$HOSTNAME\t$heuristic\t$smatch\t$edm\t$last_line" >> $corpus/eval.tsv
    done
done

