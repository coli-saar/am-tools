import matplotlib
matplotlib.use('Qt5Agg')

from itertools import groupby
from operator import itemgetter
from matplotlib.backends.backend_pdf import PdfPages
import matplotlib.pyplot as plt
import csv
import sys
import numpy as np
from statistics import geometric_mean


filename = sys.argv[1]
values = []

with open(filename,'r') as csvfile:
    plots = csv.reader(csvfile, delimiter=',')
    next(plots) # skip header
    
    for row in plots:
        values.append( (int(row[0]), int(row[1])) )

sorted_values = sorted(values, key=lambda x:x[0])
grouped_values = [(k, list(list(zip(*g))[1])) for k, g in groupby(sorted_values, itemgetter(0))]
grouped_values = [(n, np.mean(v), np.std(v)) for n,v in grouped_values]
x, means, stds = zip(*grouped_values)
        
plt.figure()
plt.clf()
#plt.yscale('log')
#plt.ylim([0,2000])
plt.errorbar(x, means, stds, linestyle='None', marker='^')

with PdfPages(filename + '.pdf') as pp:
    pp.savefig(plt.gcf())

