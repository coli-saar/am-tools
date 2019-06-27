# -*- coding: utf-8 -*-
# visualize.py
# Hiwi 2019 - visualize non-decomposable graphs (using graphviz dot)
# author                Pia Weißenhorn
# 1st email address     piaw@coli.uni-saarland.de
# 2nd email address     s8piweis@stud.uni-saarland.de
# last changed          27.06.2019
# OS                    Windows 8.1
# Python                3.7
# need Penman package (tested with Penman-0.6.2 )
# Given the stderr output of CreateCorpus extract the non-decomposable graphs
# and call graphviz dot to transform dot files to PDFs or other more readable
# file format.
# Note:INSTALLED  Penman-0.6.2 docopt-0.6.2  https://pypi.org/project/Penman/
# you can install it with   pip install Penman

# TODO: extract more information: other exceptions plus their graphs
# TODO: clean up code (main function too long, especially loop for-line-in-file
# TODO: extract only graphs with given id (cmd argument --ids ?)
# TODO: ? problem & and other html reserved token in label of node
# TODO: check correctness penman to dot conversion

import sys         # for exit
import os          # os.path.isfile ...
import subprocess  # call external commands like `dot` from graphviz tool
import logging     # log file
import argparse    # parse command line options
import penman      # extract penman formatted constants
import re          # extract sources
from timeit import default_timer as dtimer

# todo change filename of log (make input specific?
logging.basicConfig(filename="sentlength.log", filemode="w",
                    level=logging.DEBUG, format="%(levelname)s\t%(message)s")
logging.info("\t".join(["not decomposable", "ID", "SENTNO",
                        "SENTLENGTH", "LINENUMBER", "SENTENCE"]))
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
    :param line: string like 'not decomposable [Token1, Token2, Token3]'
    :return: list of tokens
    >>> string_to_list("not decomposable [Not, this, year, .]")
    ['Not', 'this', 'year', '.']
    >>> string_to_list("not decomposable [Hello, New York, !]")
    ['Hello', 'New York', '!']
    >>> string_to_list("not decomposable [a, b, c, d]")
    ['a', 'b', 'c', 'd']
    >>> string_to_list("not decomposable [ a, b , c, d ]")
    [' a', 'b ', 'c', 'd ']
    >>> string_to_list("not decomposable [a, ,, b, c, d]")
    ['a', ',', 'b', 'c', 'd']
    """
    line = line.rstrip("\n")
    line = line.replace("not decomposable ", "")
    # todo: convert assertion to if-statement (raise Exception or log warning?)
    assert(len(line) > 2 and line[0] == "[" and line[-1] == "]")
    line = line[1:-1]  # remove '[' and ']'
    toks = line.split(", ")
    return toks


def get_anno(node: str) -> tuple:
    """
    get pair of node name and annotation
    
    :param node: string like 'hello<world>'
    :return: pair with nodename and annotation like 'hello', 'world'
    >>> get_anno(node="hello<world>")
    ('hello', 'world')
    >>> get_anno(node="helloworld")
    ('helloworld', '')
    >>> get_anno(node="he_llo<wo_.rld>")
    ('he_llo', 'wo_.rld')
    """
    if "<" in node and ">" in node:
        matchobj = re.fullmatch(pattern="(.*)<(.*)>", string=node)
        if matchobj is None:
            eprint("Error during regex matching for node ", node)
            return node, ""
        else:
            nodename = matchobj.group(1)
            anno = matchobj.group(2)
            return nodename, anno
    return node, ""


def penman2dotstring(graphstr: str, typestr: str) -> str:
    """
    Convert graph in penman notation to dot notation
    
    :param graphstr: string with penman notation for graph
    :param typestr: type of graph (source annotation)
    :return: dot formatted string of that input graph
    """
    # todo: penman: special version for amr?
    # todo test: any information lost?
    # todo: what if error? empty graph, ...
    g = penman.decode(graphstr)
    gtriples = g.triples()
    graphstr = "digraph " + " {\n"
    # graphstr = "subgraph cluster_{}".format(id) + " {\n"
    intend = "  "
    graphstr += intend + "bgcolor=\"transparent\" ; rankdir = \"TD\"\n"
    graphstr += intend + "label = \"Graphtype: {}\"\n".format(typestr)
    # todo: does dot complain when there is a unknown node in edge definition?
    # todo: what does dot do if node label only introduced later?
    for src, relation, target in gtriples:  # Triple(source, relation, target)
        if relation == "instance":  # this triple is a node
            # src: node name, relation: 'instance', target: node label
            # note: node label can be None
            srcnodename, srcanno = get_anno(src)
            graphstr += intend + srcnodename
            # print label
            targetstr = str(target).strip("\"")
            targetstr = targetstr if targetstr != str(None) else ""
            # change format to <labelname <BR/><FONT COLOR="red">ROOT</FONT>>
            # todo: check if labelname or annotation contain html special chars?
            # graphstr += " [ label= \"{}\n{}\" ]".format(targetstr, srcanno)
            if srcanno != "":
                targetstr = targetstr.replace("&", "&amp;")
                graphstr += " [ label=<{} <BR/><FONT COLOR=\"red\">{}" \
                            "</FONT>>]".format(targetstr, srcanno)
            else:  # no annotation
                graphstr += " [ label= \"{}\" ]".format(targetstr)
        else:  # triple is an edge
            # src: from-node , relation : edge label, target: to-node
            srcnodename, srcanno = get_anno(src)
            trgnodename, trganno = get_anno(target)
            graphstr += intend + "{} -> {} [ label = \"{}\" ]".format(
                srcnodename, trgnodename, relation)
        graphstr += "\n"
    graphstr += "}\n"
    return graphstr


def get_graph_type_pair(line: str) -> list:
    """
    Get penman string and type of graph as pair
    
    :param line: line with penman graph with type in it
    :return: (penman-string, type)
    >>> get_graph_type_pair("[(a / neg  :lnk (b / S)  :ARG1 (c))--TYPE--(s())]")
    [('(a / neg  :lnk (b / S)  :ARG1 (c))', '(s())')]
    >>> get_graph_type_pair("[(x9<root> / _year  :lnk (exu / S))--TYPE--()]")
    [('(x9<root> / _year  :lnk (exu / S))', '()')]
    >>> get_graph_type_pair("[(x / _x  :lnk (y / _y))--TYPE--(), (z / _z  :lnk (a / _a))--TYPE--()]")
    [('(x / _x  :lnk (y / _y))', '()'), ('(z / _z  :lnk (a / _a))', '()')]
    """
    line = line.rstrip("\n")
    assert(len(line) >= 2)  # at least []
    assert (line[0] == "[" and line[-1] == "]")
    line = line[1:-1]  # delete leading [ and trailing ]
    if len(line) == 2:
        return [("", "")]  # todo what to return in case of empty graph?
    typeseparator = "--TYPE--"
    graphseparator = ", "  # assumes that type doesn't contain a whitespace,
    # otherwise this would also split types like (s(), o())
    assert(typeseparator in line)
    graphs = line.split(graphseparator)
    graphpairs = list()
    for graph in graphs:
        pair = graph.split(typeseparator)  # graphstr, typestr
        assert(len(pair) == 2)
        graphpairs.append(tuple(pair))
    return graphpairs


def write_dot_and_call(dotfile: str, outfile: str, text: str, outformat: str):
    # write to file: first tried to write all to one file, but
    # dot only outputs the first (at least pdf, png, not ps)
    # then decided for subgraphs but doesn't work because of
    # overlapping labels...
    
    # write dot to file
    with open(dotfile, mode="w", encoding="utf-8") as dotf:
        dotf.write(text)
    # call dot to create png or pdf or whatever OUTFORMAT
    # file and write output to other file
    call = ["dot", "-T" + outformat, "-Gdpi=300", "-o", outfile, dotfile]
    try:
        subprocess.run(call, check=True)
    except subprocess.CalledProcessError as e:
        eprint("WARNING: Called process error!\n", str(e))
    return


def process_constants(constants: list, filenameprefix: str, outformat: str,
                      dotindir: str, dotoutdir: str):
    # todo: input validation? dir exists
    # expect list of (word, line with graphs)
    for i, wordgraphline in enumerate(constants):
        word, graphline = wordgraphline
        graphpairs = get_graph_type_pair(graphline)
        # filename   001_const_8_lastword   8th word seen
        filename = filenameprefix + str(i) + "_" + word.replace("/", "~")
        gid = 0  # graph id
        # for each graph that was predicted for the last word
        # convert the penman style graphstr into dot format and
        # print it to dot and then call graphviz
        # Those graphs are numbered to avoid overwriting already
        # existing file/graph for lastword
        for graphstr, typestr in graphpairs:
            gid += 1
            graphdotstr = penman2dotstring(graphstr, typestr)
            dotinfile = \
                dotindir + filename + "_" + str(gid) + ".dot"
            outfile = dotoutdir + OUTPUTPREFIX + filename + "_" + str(gid) \
                + "." + outformat
            write_dot_and_call(dotfile=dotinfile, outfile=outfile,
                               text=graphdotstr,
                               outformat=outformat)
    return


def main():
    # 1. get cmd arguments
    defaultoutformat = "pdf"
    allowed_formats = ['pdf', 'png', 'jpg', 'eps', 'svg', 'bmp']
    assert (defaultoutformat in allowed_formats)
    # see https://www.graphviz.org/doc/info/output.html for all formats and
    # feel free to add some of them to allowed_formats
    defaultignoreconsts = True
    defaultminsentsize = 0
    defaultmaxsentsize = -1
    optparser = argparse.ArgumentParser(
        description="extract non-decomposable graphs and visualize them")
    optparser.add_argument("inputfile", type=str,
                           help="Path to input file (errors of CreateCorpus)")
    optparser.add_argument("outputdir", type=str,
                           help="Path to output directory")
    optparser.add_argument("--outformat", dest="outformat", type=str,
                           default=defaultoutformat, choices=allowed_formats,
                           help="Outputformat of dot (default: {})".format(
                               defaultoutformat))
    optparser.add_argument("--showconsts",
                           dest="ignoreconsts", action='store_false',
                           default=defaultignoreconsts,
                           help="Ignore constants (default: {})".format(
                               defaultignoreconsts))
    optparser.add_argument("--minsentsize", dest="minsentsize", type=int,
                           default=defaultminsentsize,
                           help="Minimum sentence size (default: {})".format(
                               defaultminsentsize))
    optparser.add_argument("--maxsentsize", dest="maxsentsize", type=int,
                           default=defaultmaxsentsize,
                           help="Maximum sentence size (default: {}) "
                                "int < 0 converted to +infinity".format(
                               defaultmaxsentsize))
    opts = optparser.parse_args()
    inputfile = opts.inputfile
    outputdir = opts.outputdir
    outformat = opts.outformat
    ignoreconsts = opts.ignoreconsts
    minsentsize = opts.minsentsize
    maxsentsize = opts.maxsentsize
    suffix_const = "_const_"
    
    print("->       INPUT FILE: ", inputfile)
    print("-> OUTPUT DIRECTORY: ", outputdir)
    print("->    OUTPUT PREFIX: ", OUTPUTPREFIX)
    print("->    OUTPUT FORMAT: ", outformat)
    print("-> Ignore constants: ", ignoreconsts)
    print("->    Min sent size: ", minsentsize)
    print("->    Max sent size: ", maxsentsize)
    
    # 2. Check if input meets requirements
    if not os.path.isfile(inputfile):
        eprint("Not a valid file path: ", inputfile)
        sys.exit(2)
    if not os.path.isdir(outputdir):
        eprint("Not a valid directory path: ", outputdir)
        sys.exit(2)
    outputdir = outputdir + "/" if not outputdir.endswith("/") else outputdir
    # todo: other checks for min max? can max == min? inclusive?
    if maxsentsize < 0:  # todo what if 0?
        eprint("Note: maxsentsize negative, set to +infinity")
        maxsentsize = float("inf")  # todo: how to represent infinity INTEGER?
    if minsentsize > maxsentsize:  # >= ?
        eprint("Maxsentsize needs to be >= than minsentsize")  # todo: >=?
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
    inside_nondecomp = False
    # todo: adapt: also other graphs now id!
    with open(file=inputfile, mode="r", encoding="utf-8") as infile:
        lines_for_curr_graph = list()
        state = 0  # 0: not inside graph, 1: non-dec, 2: const, 3: digraph
        sent_file_name_infix = None
        current_sentence = list()
        current_id = None
        lastword = None
        current_constants = list()
        sent_lengths = list()
        for line in infile:
            linenumber += 1
            if linenumber % 100000 == 0:  # show progress
                sys.stdout.write(".")
                sys.stdout.flush()
            if line.startswith("id "):
                current_id = line.rstrip("\n").replace("id ", "")
                continue
            if line.startswith("not decomposable"):  # new graph found!
                assert(not inside_nondecomp)
                inside_nondecomp = True
                assert(state == 0)
                # line is like:   not decomposable [Token1, token2, token3]
                current_sentence = string_to_list(line)
                cursentlength = len(current_sentence)
                if not(minsentsize <= cursentlength <= maxsentsize):
                    inside_nondecomp = False
                    continue
                sent_lengths.append(cursentlength)
                nondecomp_cnt += 1
                sent_file_name_infix = str(nondecomp_cnt).zfill(3)  # 001
                state = 2  # expect constants next
                lastword = None
                continue
            if line.startswith("=====end not decomposable====") and \
                    inside_nondecomp:
                inside_nondecomp = False
            if not inside_nondecomp:
                continue
            newgraph_begins = line.startswith("digraph")
            oldgraph_ends = (line.strip() == "}")  # last line of graph
            # if constants shouldn't be ignored and we expect a constant/word:
            if not ignoreconsts and state == 2 and not newgraph_begins:
                if lastword is None:  # expect word
                    lastword = line.rstrip("\n")
                else:  # expect graph constants for the last word
                    assert(lastword is not None)
                    current_constants.append((lastword, line))
                    lastword = None  # prepare for next constant
                continue
            if newgraph_begins:  # increase counter, log sentence
                state = 3
                sent = " ".join(current_sentence)
                sentence_length = len(current_sentence)
                # todo: evtl log name and path of dot outfile?
                logging.info("\t".join(["not decomposable", current_id,
                                        sent_file_name_infix,
                                        str(sentence_length),
                                        str(linenumber),
                                        sent]))
            if state == 3:
                # add current line to lines for current graph
                # (including if oldgraph_ends is True or newgraph_begins is
                # True!)
                lines_for_curr_graph.append(line)
            if oldgraph_ends:
                assert(lastword is None)
                if not ignoreconsts:
                    # process constants and write dot files
                    filepref = sent_file_name_infix + suffix_const
                    process_constants(constants=current_constants,
                                      filenameprefix=filepref,
                                      outformat=outformat, dotindir=dotinputdir,
                                      dotoutdir=dotoutputdir)
                    current_constants = list()  # clean up
                assert(state == 3)
                # 1. write graph to dot file (overwrites existing one!)
                filename = sent_file_name_infix
                dotinfile = dotinputdir + filename + ".dot"
                # (or write dot only to 1 temp file?)
                assert(len(lines_for_curr_graph) >= 2)
                # added caption to graph for whole sentence: namely the sentence
                caption = ["graph [ label=\"{}\", labelloc=top]"
                           "".format(" ".join(current_sentence))]
                lines_for_curr_graph = lines_for_curr_graph[:-1] + caption + \
                    lines_for_curr_graph[-1:]
                outfile = dotoutputdir + OUTPUTPREFIX + filename + "." + \
                    outformat
                graphdotstr = " ".join(lines_for_curr_graph)
                # does parsing of dot crash when sentence
                # contains weird characters?
                write_dot_and_call(dotfile=dotinfile, outfile=outfile,
                                   text=graphdotstr,
                                   outformat=outformat)
                # 3. clear data structures for next non-decomposable graph
                lines_for_curr_graph = list()
                current_sentence = list()
                state = 0
        # todo: what happens when last line read and still inside graph?
        assert(state == 0)
        assert(len(lines_for_curr_graph) == 0)
    endtime = dtimer()
    sys.stdout.write("\n")
    print("INFO: Took {:.3f} seconds".format(endtime - starttime))
    print("INFO: {} non decomposable graphs found".format(nondecomp_cnt))
    if len(sent_lengths) != 0:
        print("INFO: Sentence length: Mean: {:.3f} , Min: {}, Max: {}".format(
            sum(sent_lengths) / len(sent_lengths),
            min(sent_lengths), max(sent_lengths)
        ))
    
    return


if __name__ == "__main__":
    main()
