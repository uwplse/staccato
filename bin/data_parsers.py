import os.path
import os
import sys

def read_OF_stat(f_name):
    p_name = os.path.basename(f_name)
    p_name = p_name.replace(".txt", "")
    x_vals = []
    y_vals = []
    value_sum = 0
    last_mean = 0
    last_count = 0
    with open(f_name, "r") as f:
        for l in f:
            d = l.split()
            if float(d[2]) == 0:
                continue
            x_vals.append(int(d[0]))
            y_vals.append(float(d[2]))
            last_mean = float(d[6])
            last_count = int(d[7])
    return (p_name,(x_vals,y_vals,last_count * last_mean))

def read_OF_data(folder):
    files = [ f for f in  os.listdir(folder) if os.path.isfile(os.path.join(folder,f)) and \
              os.path.basename(f).startswith("tr_") ]
    totals = {}
    plots = {}
    for i in files:
        (k,(x,y,total)) = read_OF_stat(os.path.join(folder,i))
        plots[k] = y #(x,y)
        
        totals[k] = [total]
    return (plots,totals)
    # fig_id = make_graph(plots)
    # plt.figure(fig_id)
    # plt.ylim((0,25))
    # plt.savefig(out_name)
    # return sums

jforum_evs = {
    "pm_reply": [
        "Inbox request",
        "Get message page",
        "post reply page",
        "Send reply"
    ],
    "pm_send": [
        "New message page",
        "Send message"
    ],
    "post_reply": [
        "Get top posts page",
        "Get topic page",
        "Get reply page",
        "Post reply"
    ],
    "post_new_topic": [
        "Request new topic",
        "add new topic"
    ]
}

first_steps = set()
for _, sub_evs in jforum_evs.iteritems():
    first_steps.add(sub_evs[0])

jforum_ev_mapping = {}
for ev_id,sub_evs in jforum_evs.iteritems():
    for sub_ev in sub_evs:
        jforum_ev_mapping[sub_ev] = ev_id

def compute_series_avg(s_data):
    time_slice = []
    avg = []
    i = 0
    n_ev = len(s_data)
    while i < n_ev:
        accum = 0
        n_data = 0
        curr_time = s_data[i][0]
        while i < n_ev and s_data[i][0] == curr_time:
            accum += s_data[i][2]
            n_data += 1
            i += 1
        slice_avg = accum / float(n_data)
        time_slice.append(curr_time)
        avg.append(slice_avg)
    return (time_slice,avg)

def read_jforum_data(f_name):
    event_points = {}
    time_series = {}
    with open(f_name, "r") as f:
        for l in f:
            d = l.split(",")
            tstamp = int(d[0])
            d[0] = tstamp
            thread_id = d[5]
            if d[7] != "false":
                if d[2] not in event_points:
                    event_points[d[2]] = []
                event_points[d[2]].append(int(d[1]))
            if d[2] == "Login Request":
                continue
            eq = None
            if thread_id in time_series:
                eq = time_series[thread_id]
            else:
                eq = []
                time_series[thread_id] = eq
            eq.append(d)
    event_series = {}
    start_time = sys.maxsize
    for thread_id,thread_ev in time_series.iteritems():
        thread_ev.sort(key=lambda d: d[0])
        curr_ev_id = None
        curr_ev_time = 0
        i = 0
        n_ev = len(thread_ev)
        while i < n_ev:
            ev = thread_ev[i]
            ev_id = jforum_ev_mapping[ev[2]]
            curr_time = ev[0]
            if curr_time < start_time:
                start_time = curr_time
            curr_ev_id = ev_id
            curr_ev_time = 0
            is_bad = False
            is_first = True
            start_i = i
            while i < n_ev and \
                  curr_ev_id == jforum_ev_mapping[thread_ev[i][2]] and \
                  (is_first or thread_ev[i][2] not in first_steps):
                is_first = False
                ev = thread_ev[i]
                curr_ev_time += int(ev[1])
                i += 1
                if ev[7] == "false":
                    is_bad = True
                    break
            if (i - start_i) > 5:
                print "malformed data?", (i - start_i)
            if is_bad:
                continue
            e_series = None
            if ev_id in event_series:
                e_series = event_series[ev_id]
            else:
                e_series = []
                event_series[ev_id] = e_series
            e_series.append([curr_time, ev_id, curr_ev_time])
    def compute_offset(ev_chunk):
        ev_chunk[0] -= start_time
        ev_chunk[0] = ev_chunk[0] / 10000
        ev_chunk[0] *= 10
        return ev_chunk
    for (ev_id,s_data) in event_series.iteritems():
        event_series[ev_id] = map(lambda x: x[2], s_data)
#    event_series_avg = {}

    # for ev_id,s_data in event_series.iteritems():
    #     s_data.sort(key = lambda d: d[0])
    #     map(compute_offset, s_data)
    #     s_data = compute_series_avg(s_data)
    #     event_series_avg[ev_id] = s_data
    return (event_series,event_points)
    # fig_id = make_graph(event_series_avg)
    # plt.figure(fig_id)
    # plt.ylim((0,3000))
    # plt.savefig(out_name)
    # return time_sums

ss_evs = {
    "login/logout": [
        "logout request",
        "re-login request"
    ],
    "jukebox-add": [
        "get random song",
        "jukebox-control-add"
    ]
}

ss_first_ev = set()
for (k,v) in ss_evs.iteritems():
    ss_first_ev.add(v[0])
ss_ev_id_map = {}
for (k,v) in ss_evs.iteritems():
    for e in v:
        ss_ev_id_map[e] = k

def read_subsonic_data(file_name):
    time_series = {}
    start_time = sys.maxsize
    event_points = {}
    with open(file_name, "r") as f:
        for l in f:
            d = l.split(",")
            time = int(d[0])
            if d[7] != "false":
                if d[2] not in event_points:
                    event_points[d[2]] = []
                event_points[d[2]].append(int(d[1]))
                #time_points.append(int(d[1]))
            if time < start_time:
                start_time = time
            if d[2] == "Login Request":
                continue
            #response_time = int(d[1])
            thread_id = d[5]
            eq = None
            if thread_id not in time_series:
                time_series[thread_id] = []
            eq = time_series[thread_id]
            eq.append(d)
    event_series = {}
    for thread_id, thread_ev in time_series.iteritems():
        thread_ev.sort(key=lambda d:d[0])
        curr_ev_id = None
        curr_ev_time = 0
        i = 0
        n_ev = len(thread_ev)
        while i < n_ev:
            ev = thread_ev[i]
            i += 1
            ev_id = ev[2]
            series_name = ss_ev_id_map.get(ev_id, ev_id)
            if series_name not in event_series:
                event_series[series_name] = []
            e_series = event_series[series_name]
            if ev_id not in ss_ev_id_map:
                if ev[7] == "true":
                    e_series.append([int(ev[0]), ev_id, int(ev[1])])
                continue
            # ran off the end, we don't have a complete event
            if i == n_ev:
                print "MISSING NEXT FOR (2)", ev
                break
            assert ev_id in ss_first_ev, ev
            ev_start_time = int(ev[0])
            ev_name = ss_ev_id_map[ev_id]
            sub_events = ss_evs[ev_name]
            assert len(sub_events) == 2
            next_event_id = sub_events[1]
            assert sub_events[0] == ev_id
            curr_ev_time = int(ev[1])
            if ev[7] == "false":
                if thread_ev[i][2] == next_event_id:
                    i += 1
                continue
            # we don't have the next event in the sequence. weird
            elif thread_ev[i][2] != next_event_id:
                print "MISSING NEXT FOR", ev, thread_ev[i]
                #print thread_ev[i]
                continue
            elif thread_ev[i][7] == "false":
                assert thread_ev[i][2] == next_event_id
                i += 1
                continue
            else:
                assert thread_ev[i][2] == next_event_id and thread_ev[i][7] == "true"
                curr_ev_time += int(thread_ev[i][1])
                e_series.append([ev_start_time, ev_name, curr_ev_time])
                i += 1
    def compute_offset(ev_chunk):
        ev_chunk[0] -= start_time
        ev_chunk[0] = ev_chunk[0] / 10000
        ev_chunk[0] *= 10
        return ev_chunk
    event_average = {}
    for ev_id,s_data in event_series.iteritems():
#        s_data.sort(key = lambda d: d[0])
        event_series[ev_id] = map(lambda d: d[2], s_data)
        # s_data = compute_series_avg(s_data)
        # event_average[ev_id] = s_data
    return (event_series, event_points)

def read_jforum_data_aux(f_name):
    total = 0
    with open(f_name, "r") as f:
        for l in f:
            d = l.split(",")
            total += int(d[1])
    return (None, [ total ])

handlers = {
    "openfire": read_OF_data,
    "jforum": read_jforum_data,
    "subsonic": read_subsonic_data,
#    "jforum_aux": read_jforum_data_aux
}
