# AM tools

Tools for converting between various graphbanks and AM dependency trees. They are intended for use in conjunction with the [AM dependency parser](https://github.com/coli-saar/am-parser), which see for documentation.


## Building am-tools

Compile the am-tools as follows:

```
./gradlew build
```

The `./gradlew` is for Mac and Linux; on Windows, use `gradlew` instead.

This should download a bunch of dependencies and finally create a file `build/lib/am-tools.jar`, which will be quite big because it contains pretrained models for the Stanford CoreNLP system.


## Analysis and visualisation

A number of tools are available for error analysis and visualisation. Here are some:

### Find graphs that are non-decomposable because of the multiple roots problem

Class to use:
`de.saar.coli.amrtagging.formalisms.amr.tools.datascript.MakeAMConllFromAltoCorpus`
To see usage from the command line, assuming your `am-tools.jar` is in `build/libs/`, use `-h` flag.

To get a file of graph IDs that (probably) had a multiple-root problem in a given corpus, use -c for the corpus, -g for the file to print to, and set -nt and -r to false

```bash
java -cp build/libs/am-tools.jar de.saar.coli.amrtagging.formalisms.amr.tools.datascript.MakeAMConllFromAltoCorpus -h
java -cp build/libs/am-tools.jar de.saar.coli.amrtagging.formalisms.amr.tools.datascript.MakeAMConllFromAltoCorpus -c path/to/namesDatesNumbers_AlsFixed_sorted.corpus -r false -nt false -g path/to/write/changed_graph_ids.txt
```

### Visualise all aligned graphs in an amconll file

Makes pdfs using graphviz of AMR and alignments

```bash
java -cp build/libs/am-tools.jar de.saar.coli.amtools.analysis.VisualizeFromAmconll --corpus path/to/input/file.amconll -o path/to/folder/to/write/to/
```

### Visualise from Alto-readable corpus (even if the graph isn't AM-decomposable)

```bash
java -cp build/libs/am-tools.jar de.saar.coli.amtools.analysis.AlignVizAMR --help
java -cp build/libs/am-tools.jar de.saar.coli.amtools.analysis.AlignVizAMR --corpus path/to/namesDatesNumbers_AlsFixed_sorted.corpus -o path/to/output/folder/
```

### Count supertags in an amconll corpus

Prints summary of supertags and provides examples of each in a directory called `examples/`

Use the `-k` flag to count supertags with the same graph but different sources as different supertags

```bash
java -cp build/libs/am-tools.jar de.saar.coli.amtools.decomposition.analysis.CountSupertags -h
java -cp build/libs/am-tools.jar de.saar.coli.amtools.decomposition.analysis.CountSupertags -i path/to/input.amconll -o path/to/output/directory/ -k
```

Wiki on visualisation tools in am-parser: https://github.com/coli-saar/am-parser/wiki/Error-analysis:-visualization-of-AM-dependency-trees
 I think I used #2.