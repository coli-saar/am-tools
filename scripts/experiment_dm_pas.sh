#! /bin/bash

# ./scripts/experiment_dm_pas.sh <gold_sdp> <directories_with_scores.zip>


CORPUS_BASE=${CORPUS_BASE:-.} # could be /proj/irtg.shadow, but that confuses the script
GOLD_SDP=$1
JAVA_OPTIONS=${JAVA_OPTIONS:--Xmx4G}
THREADS=${THREADS:-2}

# prepwork

#rm -f /tmp/typecache.dat
HOSTNAME=`hostname`

for corpus in "${@:2}" # all except first
do
    mkdir -p $corpus
    rm -f $corpus/eval.tsv
done	      


# parsing

for heuristic in ignore_aware static trivial
do
    for corpus in "${@:2}" # all except first
    do
	csv=${corpus//\//_}.csv

	output=$($JAVA_HOME/bin/java $JAVA_OPTIONS -cp build/libs/am-tools.jar de.saar.coli.amtools.astar.Astar --print-data -s $CORPUS_BASE/$corpus/scores.zip --typecache $CORPUS_BASE/$corpus/typecache.dat --threads $THREADS --outside-estimator $heuristic --statistics $corpus/$heuristic.csv -o $corpus )
	last_line=${output##*$'\n'}
	amconll=$(echo "$last_line"|cut -f4)

	eval=$(./scripts/evaluate_dm_pas.sh $amconll out $GOLD_SDP|grep "F "|head -1)
	f=$(echo "$eval"|cut -f2 -d " ")
	echo -e "$corpus\t$HOSTNAME\t$heuristic\t$f\t$last_line" >> $corpus/eval.tsv
    done
done

