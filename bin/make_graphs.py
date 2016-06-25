import seaborn as sns
from scipy.stats.mstats import gmean
import matplotlib.pyplot as plt
import os.path
import os
import sys
import itertools
from data_parsers import handlers

sns.set_style("whitegrid")

def make_graph(plot_spec):
    fig_id = make_graph.fig_counter
    make_graph.fig_counter = make_graph.fig_counter + 1
    plt.figure(fig_id)
    for (plot_name,v) in plot_spec.iteritems():
        x_values, y_values = v
        plt.plot(x_values, y_values, label=plot_name)
    plt.legend()
    return fig_id

make_graph.fig_counter = 0


def project_series(project, inst, base):
    to_proj = None
    if project == "openfire":
        to_proj = [ "tr_authenticate",
                    "tr_chatburst" ]
    elif project == "jforum":
        to_proj = [ "post_new_topic",
                    "post_reply" ]
    fig_id = make_graph.fig_counter
    make_graph.fig_counter += 1
    plt.figure(fig_id)
    for i in to_proj:
        (x,y) = inst[i]
        plt.plot(x, y, 'o-', label=i + " inst")
        (x,y) = base[i]
        plt.plot(x, y, '^--', label=i + " orig")
    legend = plt.legend(fontsize='x-large',
                        bbox_to_anchor=(1, 1),
                        bbox_transform=plt.gcf().transFigure)
    plt.xlabel("Time (sec)", fontsize='large')
    plt.ylabel("Avg. Resp. Time (msec)", fontsize='x-large')
    plt.ylim(ymin=0)
    return fig_id

data_type = sys.argv[1]

handler = handlers[data_type]
inst_timing = sys.argv[2]
base_timing = sys.argv[3]
paper_dir = sys.argv[4]
out_dir = os.path.join(paper_dir, "graphs")
(inst_series,inst_time) = handler(inst_timing)
(base_series,base_time) = handler(base_timing)
fig = project_series(data_type, inst_series, base_series)
plt.savefig(os.path.join(paper_dir, data_type + ".png"))
