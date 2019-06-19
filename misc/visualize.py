# -*- coding: utf-8 -*-
# visualize.py
# Hiwi 2019 - visualize non-decomposable graphs (using graphviz dot)
# author                Pia Weißenhorn
# 1st email address     piaw@coli.uni-saarland.de
# 2nd email address     s8piweis@stud.uni-saarland.de
# last changed          19.06.2019
# OS                    Windows 8.1
# Python                3.7
# Given the stderr output of CreateCorpus extract the non-decomposable graphs
# and call graphviz dot to transform dot files to PDFs or other more readable
# file format.


import sys         # for exit
import os          # os.path.isfile ...
import subprocess  # call external commands like `dot` from graphviz tool
import logging     # log file
import argparse    # parse command line options
from timeit import default_timer as dtimer

# todo: change filename of log?
logging.basicConfig(filename="sentlength.log", filemode="w",
                    level=logging.DEBUG, format="%(levelname)s\t%(message)s")
logging.info("\t".join(["SENTNO", "SENTLENGTH", "LINENUMBER", "SENTENCE"]))
# sentence number is 1 for first non-decomposable sentence...

# OUTPUTPREFIX = "ndvis_"  # todo: make this command line arg?
OUTPUTPREFIX = ""


# for printing to stderr
def eprint(*args, **kwargs):
    """prints to standard error stream (use it as normal print)"""
    print(*args, file=sys.stderr, **kwargs)


def string_to_list(line: str) -> list:
    """
    converts string with list of tokens in it to list of tokens
    :param line: string like "not decomposable [Token1, Token2, Token3]
    :return: list of tokens
    >>> string_to_list("not decomposable [Not, this, year, .]")
    ['Not', 'this', 'year', '.']
    >>> string_to_list("not decomposable [a, b, c, d]")
    ['a', 'b', 'c', 'd']
    >>> string_to_list("not decomposable [ a, b , c, d ]")
    ['', 'a', 'b', '', 'c', 'd', '']
    >>> string_to_list("not decomposable [a, ,, b, c, d]")
    ['a', ',', 'b', 'c', 'd']
    """
    # todo: this is a really bad code
    def delcomma(s: str) -> str:  # delete comma separator
        if s.endswith(","):  # and len(s) != 1:
            return s[:-1]
        else:
            return s
    line = line.rstrip("\n")
    line = line.replace("not decomposable ", "")
    line = line.lstrip("[")
    line = line.rstrip("]")
    toks = line.split(" ")
    toks = [delcomma(s=t) for t in toks]  # remove trailing comma separator
    return toks


# todo extract more information
def main():
    # 1. get cmd arguments
    defaultoutformat = "pdf"
    optparser = argparse.ArgumentParser(
        description="extract non-decomposable graphs and visualize them")
    optparser.add_argument("--input", dest="inputfile", type=str,
                           help="Path to input file (errors of CreateCorpus)")
    optparser.add_argument("--outdir", dest="outputdir", type=str,
                           help="Path to output directory")
    optparser.add_argument("--outformat", dest="outformat", type=str,
                           help="Outputformat of dot (default: {})".format(
                               defaultoutformat))
    opts = optparser.parse_args()
    inputfile = opts.inputfile
    outputdir = opts.outputdir
    outformat = opts.outformat
    print("->       INPUT FILE: ", inputfile)     # debug
    print("-> OUTPUT DIRECTORY: ", outputdir)     # debug
    print("->    OUTPUT PREFIX: ", OUTPUTPREFIX)  # debug
    print("->    OUTPUT FORMAT: ", outformat)     # debug
    
    # 2. Check if input meets requirements
    allowed_formats = ['pdf', 'png', 'jpg', 'eps', 'svg', 'bmp']
    assert(defaultoutformat in allowed_formats)
    # see https://www.graphviz.org/doc/info/output.html for all formats and
    # feel free to add some of them to allowed_formats
    if outformat not in allowed_formats:
        eprint("Specified output format is currently not allowed!")
        eprint("{} - not valid, use default: {}".format(
            outformat, defaultoutformat))
        eprint("Allowed formats: ", " ".join(allowed_formats))
        outformat = defaultoutformat
    outputdir = outputdir + "/" if not outputdir.endswith("/") else outputdir
    if not os.path.isfile(inputfile):
        eprint("Not a valid file path: ", inputfile)
        sys.exit(2)
    if not os.path.isdir(outputdir):
        eprint("Not a valid directory path: ", outputdir)
        sys.exit(2)
    
    # 3. Read input file ...
    nondecomp_cnt = 0
    linenumber = 0
    dotinputdir = outputdir + "dot/"
    dotoutputdir = outputdir + outformat + "/"
    for dotdir in [dotinputdir, dotoutputdir]:
        if not os.path.exists(dotdir):
            os.mkdir(dotdir)
    
    starttime = dtimer()
    with open(file=inputfile, mode="r") as infile:
        lines_for_curr_graph = list()
        inside_graph = False
        sent_file_name_infix = None
        current_sentence = list()
        sent_lengths = list()
        for line in infile:
            linenumber += 1
            if line.startswith("not decomposable"):
                # not decomposable [Token1, token2, token3]
                current_sentence = string_to_list(line)
                sent_lengths.append(len(current_sentence))
                continue
            newgraph_begins = line.startswith("digraph")
            oldgraph_ends = (line.strip() == "}")  # last line of graph
            if newgraph_begins:  # increase counter, log sentence
                inside_graph = True
                nondecomp_cnt += 1
                sent_file_name_infix = str(nondecomp_cnt).zfill(3)
                sent = " ".join(current_sentence)
                sentence_length = len(current_sentence)
                # todo: evtl log name and path of dot outfile?
                logging.info("\t".join([sent_file_name_infix,
                                        str(sentence_length),
                                        str(linenumber),
                                        sent]))  # todo: add msg 'nondecomp'?
            if inside_graph:
                # add current line to lines for current graph
                # (including if oldgraph_ends is True or newgraph_begins is
                # True!)
                lines_for_curr_graph.append(line)
            if oldgraph_ends:
                # 1. write graph to dot file (overwrites existing one!)
                dotinputfile = dotinputdir + sent_file_name_infix + ".dot"
                # (or write dot only to 1 temp file?)
                assert(len(lines_for_curr_graph) >= 2)
                with open(dotinputfile, mode="w", encoding="utf-8") as dotf:
                    for i, l in enumerate(lines_for_curr_graph):
                        # write caption (value: sentence) just before last '}'
                        if i == len(lines_for_curr_graph)-1:
                            # does parsing of dot crash when sentence
                            # contains weird characters?
                            dotf.write(
                                "graph [ label=\"{}\", labelloc=top]".format(
                                    " ".join(current_sentence)))
                        # write line
                        dotf.write(l)
                # 2. call dot to create png or pdf or whatever OUTFORMAT file
                #    and write output to other file
                call = ["dot", "-T" + outformat, "-Gdpi=300",
                        "-o", dotoutputdir + OUTPUTPREFIX +
                        sent_file_name_infix + "." + outformat,
                        dotinputfile
                        ]
                subprocess.run(call)  # todo catch errors if any?
                # 3. clear data structures for next non-decomposable graph
                lines_for_curr_graph = list()
                inside_graph = False
                current_sentence = list()
        # todo: what happens when last line read and still inside graph?
        assert(not inside_graph)
        assert(len(lines_for_curr_graph) == 0)
    endtime = dtimer()
    print("INFO: Took {:.3f} seconds".format(endtime - starttime))
    print("INFO: {} Non-decomposable graphs found".format(nondecomp_cnt))
    if len(sent_lengths) != 0:
        print("INFO: Sentence length: Mean: {:.3f} , Min: {}, Max: {}".format(
            sum(sent_lengths) / len(sent_lengths),
            min(sent_lengths), max(sent_lengths)
        ))
    
    return


if __name__ == "__main__":
    main()
