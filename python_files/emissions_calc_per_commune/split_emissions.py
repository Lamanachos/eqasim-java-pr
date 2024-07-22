import os
import xml.etree.ElementTree as ET
import time as t
import gzip
import attributes as attrib
import multiprocessing as mp

def write_file(temp_emissions_file, childs, i):
    events_to_keep = attrib.events_to_keep
    #start = t.time()
    f = open(temp_emissions_file,'wb')
    f.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".encode())
    f.write("<events version='1.0'>\n".encode())
    for child in childs:
        if child.attrib["type"] in events_to_keep:
            f.write(ET.tostring(child))
    f.write("</events>".encode())
    f.close()
    #print(f"File {i} built in {t.time()-start} seconds")

if __name__ == "__main__":
    true_start = t.time()
    
    events_to_keep = attrib.events_to_keep
    emissions_file = attrib.emissions_file
    emissions_split_folder_output = attrib.emissions_split_folder_output
    if not os.path.exists(emissions_split_folder_output):
        os.makedirs(emissions_split_folder_output)
    print("Parsing emissions...")
    unzip_file = gzip.open(emissions_file)
    tree = ET.parse(unzip_file)
    #usually takes around 243 seconds
    print("Parsing done in ",t.time()-true_start," seconds")
    root = tree.getroot()
    count = 0
    row_count = 0
    nb_break = attrib.nb_break
    print("Building emissions files...")
    start = t.time()
    pool = mp.Pool(mp.cpu_count())
    inputs = []
    for i in range((len(root)//nb_break)+1):
        temp_emissions_file = emissions_split_folder_output + "\\" + str(i) + ".xml"
        start_l = int(i*nb_break)
        end_l = int(min(len(root),(i+1)*nb_break+1))
        childs = root[start_l:end_l]
        inputs.append([temp_emissions_file,childs,i])
    try:
        pool.starmap(write_file, inputs)
    finally:
        pool.close()
        pool.join()
    print("Files built in",t.time()-start,"seconds")
    print(f"Total time : {t.time()-true_start}")
