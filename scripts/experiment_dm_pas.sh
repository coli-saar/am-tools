#! /bin/bash

CORPUS_BASE=${CORPUS_BASE:-/proj/irtg.shadow}
GOLD_SDP=$1
JAVA_OPTIONS=-Xmx4G
THREADS=2

rm -f /tmp/typecache.dat


for heuristic in ignore_aware static trivial
do


    for corpus in "${@:2}" # all except first
    do
	csv=${corpus//\//_}.csv
	mkdir -p $corpus
	rm -f $corpus/eval.tsv

	output=$($JAVA_HOME/bin/java $JAVA_OPTIONS -cp build/libs/am-tools.jar de.saar.coli.amtools.astar.Astar --print-data -s $CORPUS_BASE/$corpus/scores.zip --typecache /tmp/typecache.dat --threads $THREADS --outside-estimator $heuristic --statistics $corpus/$heuristic.csv -o $corpus )
	last_line=${output##*$'\n'}
	amconll=$(echo "$last_line"|cut -f4)

	eval=$(./scripts/evaluate_dm_pas.sh $amconll out $GOLD_SDP|grep "F "|head -1)
	f=$(echo "$eval"|cut -f2)
	echo -e "$corpus\t$heuristic\t$f\t$last_line" >> $corpus/eval.tsv
    done
done

