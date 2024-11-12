from diagnosis import minimalDiagnoses, diagnosis, helperFunctions
import os
import clingo
from shutil import copyfile
import dill as pickle
import sys

list_of_added_knowledge=[]
asp_file=''
list_of_difference_white=[]

def impact_function(file_path, input_text):
    global input_list_original, input_list, list_of_added_knowledge, asp_file, list_of_difference_white

    asp_file = file_path
    minimalDiagnoses.fetch_globals()
    minimalDiagnoses.get_added_knowledge_function()
    list_of_added_knowledge = minimalDiagnoses.list_of_added_knowledge
    list_of_difference_white = minimalDiagnoses.list_of_difference_white

    input_list_original= input_text.split("/")
    input_list = helperFunctions.add_point(helperFunctions.handle_input_negation(input_list_original))
    input_list_tmp =[]
    rem_atom_list = []
    for e in input_list:
        try:
            atomId = e[e.index("alpha")+len("alpha"):e.index(".")]
        except ValueError or IndexError:
            sys.exit(2)
        
        if "not" in e:
            r_atom = f"remove({atomId})."
            a_atom = f"not alpha{atomId}()."
        else:
            r_atom = f"not remove({atomId})."
            a_atom = f"alpha{atomId}()."
        input_list_tmp.append(r_atom)
        input_list_tmp.append(a_atom)
        rem_atom_list.append(r_atom)
    input_list = [e for e in input_list_tmp if e]
    rem_atom_list_valid = [e for e in input_list if e in minimalDiagnoses.allowed_entries]

    # check if any invalid facets/axioms were input
    if rem_atom_list != rem_atom_list_valid: 
        sys.exit(2)
    what_if_delete()

def what_if_delete():
    """
    this function returns the difference between a program befor some deletion and after it
    :return: 
    """
    global list_of_added_knowledge, asp_file, input_list, what
    diagnosis.create_original(list_of_added_knowledge, asp_file)
    tmp_asp_path = asp_file[:asp_file.rfind(os.sep) + 1]
    tmp_asp_file = tmp_asp_path + "what_if.txt"
    if os.path.exists(tmp_asp_file):
        os.remove(tmp_asp_file)

    copyfile(tmp_asp_path + "original_asp_program.txt", tmp_asp_file)

    with open(tmp_asp_file, "a") as tmp:
        for e in helperFunctions.add_point([element for element in list_of_added_knowledge if element not in input_list]):
            tmp.write(":- " + helperFunctions.negate(e) + "\n")
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


    for e in list(set(helperFunctions.add_point(diagnosis.converter(list_of_difference_white))).difference(helperFunctions.add_point(what_if_white))):
        if e not in list_of_added_knowledge:
           impact_li.append(e)
    impacts_li = helperFunctions.transform_facets(impact_li)
    write_impacts("impacts.txt", input_list_original, impacts_li)

def write_impacts(file_name, input_list_original, impact_list):    
    with open(file_name, "w") as f:
        f.write("Remove:\n")
        for e in input_list_original:
            f.write(e+"\n")

        f.write("Impact:\n")
        for e in impact_list:
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

