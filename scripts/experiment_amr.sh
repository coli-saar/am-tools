#! /bin/bash

# ./scripts/experiment_amr.sh <wordnet_path> <lookup> <output_dir> <directories_with_scores.zip>
#
# e.g.: ./scripts/experiment_amr.sh ../am-parser/downloaded_models/wordnet3.0/dict EMNLP20/AMR-2015/lookup EMNLP20/AMR-2015/test EMNLP20/AMR-2015/test/*_amr15

# change these variables away from their default values by setting them explicitly,
# e.g. THREADS=10 ./scripts/experiment_amr.sh ...
CORPUS_BASE=${CORPUS_BASE:-.}
JAVA_OPTIONS=${JAVA_OPTIONS:--Xmx4G}
THREADS=${THREADS:-2}
LIMIT_ITEMS=${LIMIT_ITEMS:-0}

WORDNET=$1
LOOKUP=$2
OUTDIR=$3


# prepwork

#rm -f /tmp/typecache.dat
HOSTNAME=`hostname`

for corpus in "${@:4}" # all except first three
do
    mkdir -p $corpus
    rm -f $corpus/eval.tsv
done	      


# parsing

for heuristic in ignore_aware static trivial
do
    for corpus in "${@:4}" # all except first
    do
	csv=${corpus//\//_}.csv

	output=$($JAVA_HOME/bin/java $JAVA_OPTIONS -cp build/libs/am-tools.jar de.saar.coli.amtools.astar.Astar --print-data -s $CORPUS_BASE/$corpus/scores.zip --typecache $CORPUS_BASE/$corpus/typecache.dat --limit-items $LIMIT_ITEMS --threads $THREADS --outside-estimator $heuristic --statistics $corpus/$heuristic.csv -o $corpus )

	last_line=${output##*$'\n'}
	amconll=$(echo "$last_line"|cut -f5)
	eval=$(./scripts/evaluate_amr.sh $WORDNET $LOOKUP $OUTDIR $amconll|grep "F-score: "|head -1)
	f=$(echo "$eval"|cut -f2 -d " ")
	
	echo -e "$corpus\t$HOSTNAME\t$heuristic\t$f\t$last_line" >> $corpus/eval.tsv
    done
done

