# README 

## What is `visualize.py` for?
The `de.saar.coli.amrtagging.formalisms.*.tools.CreateCorpus`  
(see also `am-parser.wiki/Converting-individual-formalisms-to-AM-CoNLL.md`)
output contains error messages if a graph was not decomposable.
```
mkdir eds
java -cp build/libs/am-tools-all.jar de.saar.coli.amrtagging.formalisms.eds.tools.CreateCorpus -c <eds_train.amr.txt> -o ./eds/ 2> errorfile.txt
```
This output to stderr contains the graphs is dot format that were not 
decomposable. The `visualize.py`script extracts those dot formatted graphs and 
actually uses dot to convert them into more readable pdfs (or pngs if you like).
Moreover, this script prints out the concrete sentences 
belonging to the non-decomposable graphs as well as 
the number of tokens (useful to search for minimal not-working examples).

Note: If the above `createCorpus` command results in a java `OutOfMemoryError`
you might want to allow java to occupy more memory.  If you add the option 
`-Xmx3g`, java can use up to `3g`(i.e. 3GB) of memory.


## How to run
- This script was developed using Python 3.7
- `graphviz` needs to be installed, since the `dot` executable is called 
from the python script. You can download graphviz from 
[http://graphviz.org/download/](http://graphviz.org/download/)

General usage:  
`python3 visualize.py --input INPUTFILE --outputdir OUTPUTDIR`
or if you would like another output format than PDF use  
`python3 visualize.py --input INPUTFILE --outputdir OUTPUTDIR --outformat OUTFORMAT`


### Example usages
(requires specific directory structure)  
`python3 visualize.py --input ../visualizer_data/input/eds/errfile.txt --outdir ../visualizer_data/output/eds --outformat pdf`
`python3 visualize.py --input ../visualizer_data/input/eds/errfile.txt --outdir ../visualizer_data/output/eds --outformat png`


### Input
File with all error messages (everything that was redirected from stderr to
 a file) from the `CreateCorpus` call.

### Output
- a `sentlength.log` file in tab separated format:  
  for each non-decomposable graph prints one line:  
  `INFO<TAB>SENTNO<TAB>SENTLENGTH<TAB>LINENUMBER<TAB>SENTENCE`  
  e.g. `INFO<TAB>004<TAB>4<TAB>659<TAB>Not this year .`  
  (4th non decomposable graph, four tokens, found at line number 659 of the 
  input file, then all tokens separated by whitespace)
- under the specified `OUTPUTDIR` two folders are created: 
    - a `dot` folder  containing the graphs extracted form the input file and
    - a `pdf` (or `png` or whatever format corresponding to the 
    specified `outformat`) folder containing the output of graphviz dot
    - Note: I've added a caption (namely the whitespace separated tokens of the 
    sentence) to the dot output.
    - for each nondecomposable graph the corresponding files start with the 
    respective `SENTNO` (e.g. `004`)

Note: Currently, ignores the rest of the error messages (alignments)

## Author
Pia Weißenhorn
piaw@coli.uni-saarland.de
