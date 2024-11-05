from diagnosis import minimalDiagnoses, diagnosis
import os
import clingo
from shutil import copyfile
import re
import dill as pickle

list_of_added_knowledge=[]
asp_file=''
list_of_difference_white=[]

def impact_function(file_path, input_text):
    global input_list_original, input_list, list_of_added_knowledge, asp_file, list_of_difference_white
    asp_file = file_path
    fetch_globals()
    get_added_knowledge_function()
    input_list_original= input_text.split("/")
    input_list = minimalDiagnoses.add_point(handle_input_negation(input_list_original))
    input_list_tmp =[]
    for e in input_list:
        atomId = e[e.index("alpha")+len("alpha"):e.index(".")]
        if "not" in e:
            r_atom = f"remove({atomId})"
            a_atom = f"not alpha{atomId}()"
        else:
            r_atom = f"not remove({atomId})"
            a_atom = f"alpha{atomId}()"
        input_list_tmp.append(r_atom)
        input_list_tmp.append(a_atom)
    input_list = [e for e in input_list_tmp if e]
    what_if_delete()

def what_if_delete():
    """
    this function returns the difference between a program befor some delletion and after it
    :return: 
    """
    global list_of_added_knowledge, asp_file, input_list, what
    start = '\033[95m'
    end = '\033[0m'
    diagnosis.create_original(list_of_added_knowledge, asp_file)
    tmp_asp_path = asp_file[:asp_file.rfind(os.sep) + 1]
    tmp_asp_file = tmp_asp_path + "what_if.txt"
    if os.path.exists(tmp_asp_file):
        os.remove(tmp_asp_file)

    copyfile(tmp_asp_path + "original_asp_program.txt", tmp_asp_file)

    with open(tmp_asp_file, "a") as tmp:
        for e in minimalDiagnoses.add_point([element for element in list_of_added_knowledge if element not in minimalDiagnoses.add_point(input_list)]):
            tmp.write(":- " + minimalDiagnoses.negate(e) + "\n")
    tmp.close()

    args = ['--enum-mode=cautious']
    prg = clingo.Control(args)
    try:
        prg.load(tmp_asp_file)
    except RuntimeError:
        return None
    prg.ground([("base", []), ("parts", [])])
    prg.solve(on_model=model_what_if)

    impact_li = []


    for e in list(set(minimalDiagnoses.add_point(diagnosis.converter(list_of_difference_white))).difference(minimalDiagnoses.add_point(what_if_white))):
        if e not in list_of_added_knowledge:
           impact_li.append(e)
    save_impact(impact_li)

def save_impact(impact_li):
    impacts = []
    for element in impact_li:
        if "alpha" in element:
            impacts.append(element[:element.index('(')])
        if "remove" in element:
            e_id = element[element.index('(')+1:element.index(')')]
            if "not" in element:
                impacts.append(f"alpha{e_id}")
            else:
                impacts.append(f"not alpha{e_id}")
    
    with open("impacts.txt", "w") as f:
        f.write("Remove:\n")
        for e in input_list_original:
            f.write(e+"\n")

        f.write("Impact:\n")
        for e in impacts:
            f.write(e+"\n")



def model_what_if(model):
    """

    :param model: 
    :return: 
    """
    assert isinstance(model, clingo.Model)
    global what_if_white
    what_if_white = []
    for atom in model.symbols(atoms=True):
        if atom.negative:
            tmp = "not"
        else:
            tmp = ""
        for argument in [atom.arguments]:
            x = ""
            for part in argument:
                x = x + str(part) + ','
            x = x[:-1]
            what_if_white.append(tmp + atom.name + "(" + x + ")")

def handle_input_negation(some_list):
    """
    this function handles negated input
    :param some_list: 
    :return: 
    """
    ret = []
    for i in some_list:
        if i[:4] == "not ":
            if i.count("not ") % 2 != 0:
                i = re.sub('not ', '', i).strip()
                i = "not " + i
            else:
                i = re.sub('not ', '', i).strip()
        ret.append(i)
    if len(ret) == 1 and (ret[0] == "not " or ret[0] == ""):
        ret = []
    return ret

def get_added_knowledge_function():
    global list_of_added_knowledge
    with open("added_knowledge.txt", "r") as f:
        lines = f.readlines()
        for line in lines:
            list_of_added_knowledge.append(line.strip())

def fetch_globals():
    global first_ever, first_answer_set_ever, first_list_of_predicates, list_of_answer_sets, list_of_predicates,\
    list_of_difference_blue, list_of_difference_red, list_of_difference_white, tmp_prev_red, tmp_prev_white, allowed_entries
    with open("pyglobals.pk1", 'rb') as f:
        data = pickle.load(f)
        first_ever = data['first_ever']
        first_answer_set_ever = data['first_answer_set_ever']
        # last_answer_set = data['last_answer_set']
        first_list_of_predicates = data['first_list_of_predicates']
        list_of_answer_sets = data['list_of_answer_sets']
        list_of_predicates = data['list_of_predicates']
        list_of_difference_blue = data['list_of_difference_blue']
        list_of_difference_red = data['list_of_difference_red']
        list_of_difference_white = data['list_of_difference_white']
        tmp_prev_red = data['tmp_prev_red']
        tmp_prev_white = data['tmp_prev_white']
        allowed_entries = data['allowed_entries']

