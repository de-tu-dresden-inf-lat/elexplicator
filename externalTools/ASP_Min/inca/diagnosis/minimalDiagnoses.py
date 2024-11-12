#from scipy.weave.catalog import intermediate_dir

from diagnosis import diagnosis, correctionset, helperFunctions
import os
import clingo
import ast
import operator
import dill as pickle
import sys


logs = []
union_of_answers_without_c = []
list_of_indices = []
list_of_added_knowledge = []
list_of_difference_blue = []
list_of_difference_red = []
list_of_difference_white = []
tmp_prev_red = []
tmp_prev_blue = []
tmp_prev_white = []
list_of_answer_sets = []
list_of_predicates_not_to_negate = []
list_of_predicates = []
first_ever = []
first_answer_set_ever = []
last_answer_set = []
allowed_entries = {}
init_first_answer_set = True
conf_pr = True
was_sat = ""
consequences = []
what_if_white = []
first_list_of_predicates = []
justifications_program_path =''
input_facet =''

minimal_conflict_sets_asp = []

def get_all_minimal_diagnoses(max_index, justifications_program_path, mDsID, min_diag, facet_diag, first_run, input_facet):
    """
    Generate all optimal classical repairs for making "not statement()" a valid option
    :param max_index:
    :param justifications_program_path:
    :return:
    """
    global all_optimal_classical_repairs, optimal_classical_repairs_file_path, intermediate_optimal_classical_repairs, first_ever, \
    first_answer_set_ever, last_answer_set, init_first_answer_set, first_list_of_predicates, list_of_answer_sets, list_of_predicates,\
    list_of_difference_blue, list_of_difference_red, list_of_difference_white, tmp_prev_red, tmp_prev_white, allowed_entries, list_of_predicates_not_to_negate, facet
    all_optimal_classical_repairs = []
    intermediate_optimal_classical_repairs = []
    # path = justifications_program_path[:justifications_program_path.rfind(os.sep) + 1]
    # optimal_classical_repairs_file_path = path + mDsID
    optimal_classical_repairs_file_path =  mDsID

    init_first_answer_set = first_run
    deep_investigation = not init_first_answer_set
    if input_facet is not None:
        facet = input_facet

    if os.path.exists(optimal_classical_repairs_file_path):
        os.remove(optimal_classical_repairs_file_path)

    justifications_program_path = justifications_program_path

    if min_diag or first_run:
        program = open(justifications_program_path, "a")

        program.write("\n:- not remove(_).\n")
        for i in range(0, max_index+1):
            program.write("remove(" + str(i) + "):- " + "not alpha"+str(i) + ".\n")

        program.write(":- statement.\n")
        program.close()


    if facet_diag and not first_run and os.path.exists("pyglobals.pk1"):
        get_added_knowledge_function()
        fetch_globals()

    if facet_diag:
        if input_facet is None:
            res=translator(justifications_program_path, False)
        else:
            res = translator(justifications_program_path, deep_investigation)
        store_globals()
        
    return compute_all_optimal_classical_repairs(justifications_program_path, max_index+1)

def get_added_knowledge_function():
    global list_of_added_knowledge
    try:
        with open("added_knowledge.txt", "r") as f:
            lines = f.readlines()
            for line in lines:
                list_of_added_knowledge.append(line.strip())
    except FileNotFoundError as e:
        sys.exit(1)

def compute_all_optimal_classical_repairs(program_path, len_original):
    """

    :param program_path:
    :param len_original:
    :return:
    """
    global intermediate_optimal_classical_repairs
    global added_ic 
    added_ic = []

    for i in range(1, len_original+1):
        args = ['--models=0', '-t 4']
        #prg = diagnosis.clingo.Control(args)
        prg = clingo.Control(args)
        try:
            prg.load(program_path)
        except RuntimeError as rte:
            print(rte)
            return "Parsing Problem"
        prg.ground([("base", []), ("parts", [])])
        tester = ":- not " + str(i) + "#count{X:remove(X)}" + str(i) + "."
        prg.add("", [], tester)
        prg.ground([("", [])])
        try:
            ret = str(prg.solve())
        except RuntimeError:
            ret = "Something is wrong!"
        if ret == "SAT":
            # get current repairs
            str(prg.solve(on_model=extract_axioms_identifiers))
            # add them as new integrity constraints            
            add_new_integrity_constraints(program_path)
            # check if there are more repairs of larger size
            if are_there_more_repairs(program_path):
                intermediate_optimal_classical_repairs = []
            else:
                with open(program_path, 'r') as just_file:
                    lines = just_file.readlines()
                filter_lines = [line for line in lines if line not in added_ic]
                with open(program_path, 'w') as just_file:
                    just_file.writelines(filter_lines)

                ocrs = open(optimal_classical_repairs_file_path, "a")
                for repair in all_optimal_classical_repairs:
                    for identifier in repair:
                        ocrs.write(identifier)
                    ocrs.write("\n")
                ocrs.close()
                break


def are_there_more_repairs(program_path):
    """

    :param program_path:
    :return:
    """
    args = ['--models=1']
    #prg = diagnosis.clingo.Control(args)
    prg = clingo.Control(args)
    try:
        prg.load(program_path)
    except RuntimeError as rte:
        print(rte)
        return "Parsing Problem"
    prg.ground([("base", []), ("parts", [])])
    try:
        ret = str(prg.solve())
    except RuntimeError:
        ret = "Something is wrong!"

    if ret == "SAT":
        return True
    return False


def extract_axioms_identifiers(model):
    """

    :param model:
    :return:
    """
    repair = []

    for atom in model.symbols(shown=True):
        if "remove(" in str(atom):
            repair.append(str(atom)[str(atom).index("(")+1:str(atom).index(")")])
            repair.append(", ")

    repair = repair[0:len(repair)-1]

    if repair not in all_optimal_classical_repairs:
        all_optimal_classical_repairs.append(repair)
        intermediate_optimal_classical_repairs.append(repair)

def add_new_integrity_constraints(program_path):
    """

    :param program_path:
    :return:
    """
    program = open(program_path, "a")

    for constraint in get_integrity_constraints(intermediate_optimal_classical_repairs):
        program.write(constraint)
        added_ic.append(constraint)
        

    program.close()


def get_integrity_constraints(repairs):
    """

    :param repairs:
    :return:
    """
    integrity_constraints = []

    for repair in repairs:
        integrity_constraint = ""
        for i in range(0, len(repair)):
            if repair[i] != ", ":
                integrity_constraint += "not alpha" + repair[i] + "(), "

        integrity_constraints.append(":- " + integrity_constraint[0:len(integrity_constraint) - 2] + ".\n")

    return integrity_constraints



def translator(asp_file, deep_investigation):
    global list_of_answer_sets, first_answer_set_ever, init_first_answer_set, first_list_of_predicates
    translator_not(asp_file)
    list_of_answer_sets = []
    args = ['--enum-mode=brave']
    prg = clingo.Control(args)
    try:
        prg.load(asp_file)
    except RuntimeError as rte:
        print(rte)
        return "Parsing Problem"

    prg.ground([("base", []), ("parts", [])])
    ret = prg.solve(on_model=create_last_answer_set)
    if str(ret) == "SAT":
        model_function(last_answer_set[len(last_answer_set) - 1])
        if init_first_answer_set:
            first_list_of_predicates = list_of_predicates
            init_predicates_names()
            first_answer_set_ever = list_of_answer_sets[len(list_of_answer_sets) - 1]
            answer_set_li = []

        if len(first_answer_set_ever) != 0 and len(list_of_answer_sets) != 0 and not init_first_answer_set:
            if len(list_of_answer_sets[0]) != 0:
                compare(first_answer_set_ever, list_of_answer_sets[len(list_of_answer_sets) - 1], deep_investigation)
            init_first_answer_set = False 
    initial_display(str(ret))
    return str(ret)

def model_function(model):
    """
    
    :param model: 
    :return: 
    """
    found = False
    global list_of_predicates, list_of_predicates_not_to_negate
    list_of_predicates = []
    for atom in model: # model.symbols(atoms=True):
        if atom.negative:
            tmp = "-"
        else:
            tmp = ""
        predicate = tmp + str(atom)
        if not list_of_predicates:      

            predicate = Predicate(tmp + atom.name)
            Predicate.add_elements(predicate, atom.arguments)

            if predicate.p_name + "(" + predicate.p_elements[len(predicate.p_elements) - 1] + ")" not in first_ever:

                list_of_predicates.append(predicate)

                predicate = Predicate(helperFunctions.negate(tmp + atom.name))
                Predicate.add_elements(predicate, atom.arguments)

                list_of_predicates.append(predicate)
        else:
            for i in range(len(list_of_predicates)):
                if list_of_predicates[i].p_name == atom.name:
                    predicate = list_of_predicates[i]
                    Predicate.add_elements(predicate, atom.arguments)
                    if predicate.p_name + "(" + predicate.p_elements[len(predicate.p_elements) - 1] + ")" not in first_ever:
                        name_in_list = [x for x in list_of_predicates if x.p_name == helperFunctions.negate(atom.name)]
                        if len(name_in_list) > 0:
                            predicate = name_in_list[0]
                        else:
                            predicate = Predicate(helperFunctions.negate(tmp + atom.name))
                            list_of_predicates.append(predicate)
                        Predicate.add_elements(predicate, atom.arguments)
                    found = True
                    break

            if not found:
                predicate = Predicate(tmp + atom.name)
                Predicate.add_elements(predicate, atom.arguments)
                if predicate.p_name + "(" + predicate.p_elements[len(predicate.p_elements) - 1] + ")" not in first_ever:

                    list_of_predicates.append(predicate)

                    predicate = Predicate(helperFunctions.negate(tmp + atom.name))
                    Predicate.add_elements(predicate, atom.arguments)

                    list_of_predicates.append(predicate)
            found = False
    list_of_answer_sets.append(list_of_predicates)
    li_preds = []
    return None

def init_predicates_names():
    """
    This function initialises the list of predicate names according to the first run of the program without the input of the user
    :return: 
    """
    global list_of_predicates_names, first_list_of_predicates
    list_of_predicates_names = []
    # should add "-" "not" "not -" to the names of predicates and add them to the list or not???
    for pr in first_list_of_predicates:
        list_of_predicates_names.append(pr.p_name)
    for i in range(len(list_of_predicates_names)):
        list_of_difference_blue.append([])
        list_of_difference_red.append([])
        list_of_difference_white.append([])
        tmp_prev_red.append([])

def translator_not(asp_file):
    """
    determine which predicates should not be negated
    :param asp_file:
    :return:
    """
    global list_of_predicates_not_to_negate, first_ever
    list_of_predicates_not_to_negate = []
    args = ['--enum-mode=cautious']
    prg = clingo.Control(args)
    try:
        prg.load(asp_file)
    except RuntimeError:
        return None
    prg.ground([("base", []), ("parts", [])])
    ret = prg.solve(on_model=model_function_not)
    if str(ret) == "SAT":
        if init_first_answer_set:
            first_ever = list_of_predicates_not_to_negate


def model_function_not(model):
    """

    :param model: 
    :return: 
    """
    assert isinstance(model, clingo.Model)
    global list_of_predicates, list_of_predicates_not_to_negate
    list_of_predicates_not_to_negate = []
    
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
            list_of_predicates_not_to_negate.append(tmp + atom.name + "(" + x + ")")

def create_last_answer_set(model):
    """
    just find the last Answer set
    :param model: 
    :return: 
    """
    assert isinstance(model, clingo.Model)
    global last_answer_set
    last_answer_set = [model.symbols(atoms=True)]

def compare(old_model, new_model, deep_investigation):
    """
    This method compares the last new generated model with the last previous model and detects the missing predicates or
    the ones added newly
    :param old_model: 
    :param new_model: 
    :param deep_investigation:
    :return: 
    """
    global tmp_prev_red, tmp_prev_blue, list_of_difference_red, list_of_difference_blue, list_of_indices, list_of_difference_white, input_list,\
        asp_file_name, union_of_answers_without_c, tmp_prev_white, facet

    if len(old_model) > len(new_model):
        for e in old_model:
            if e.p_name not in [n.p_name for n in new_model]:
                new_model.append(Predicate(e.p_name))

    old_model.sort(key=operator.attrgetter('p_name'))
    new_model.sort(key=operator.attrgetter('p_name'))
    for i in range(len(old_model)):
        list_of_indices.append(PredicateIndex(old_model[i].p_name, i))

    for i in range(len(old_model)):
        list_of_difference_blue[i] = []
        list_of_difference_red[i] = []
        list_of_difference_white[i] = []
        tmp_prev_red[i] = []

    if list_of_added_knowledge:

        for i in range(len(old_model)):

            if old_model[i].p_name == new_model[i].p_name and old_model[i].p_name[:4] != "not ":

                old_model[i].p_elements.sort()
                new_model[i].p_elements.sort()

                for e in [old_model[i].p_name + "(" + str(j) + ")" for j in old_model[i].p_elements if j not in new_model[i].p_elements]:
                    list_of_difference_red[i].append(e)
                for e in [old_model[i].p_name + "(" + str(j) + ")" for j in old_model[i].p_elements if
                          j not in [m for m in old_model[i].p_elements if m not in new_model[i].p_elements]]:
                    list_of_difference_blue[i].append(e)
                list_of_difference_white[i] = list(set(list_of_predicates_not_to_negate).intersection(list_of_difference_blue[i]))
                list_of_difference_blue[i] = list(set(list_of_difference_blue[i]).difference(set(list_of_difference_white[i])))

                list_of_difference_red[i] = [e for e in list_of_difference_red[i] if (e not in list_of_difference_blue[i] and e in tmp_prev_red[i]) or
                                          (e not in tmp_prev_red[i])]

                tmp_prev_red[i] = [e for e in list_of_difference_red[i] if e not in tmp_prev_red[i]]

        # predicate names that start with a letter greater than n

        for i in range(len(old_model)):

            if old_model[i].p_name == new_model[i].p_name and old_model[i].p_name[:4] == "not ":
                ind = [x.p_index for x in list_of_indices if x.p_name == old_model[i].p_name[4:]][0]
                tmp = []
                for e in range(len(list_of_difference_blue[ind])):
                    tmp.append(helperFunctions.negate(list_of_difference_blue[ind][e]))
                for e in tmp:
                    list_of_difference_blue[i].append(e)

                tmp_blue = []
                p_name = helperFunctions.negate(new_model[i].p_name)
                ind = [x for x in list_of_indices if x.p_name == p_name][0].p_index

                for argument in new_model[ind].p_elements:
                    tmp_blue.append(new_model[ind].p_name + "(" + argument + ")")

                for_sure_blue = list_of_difference_white[ind]

                for element in for_sure_blue:

                    predicate_name = element[:element.index("(")]
                    predicate_name = helperFunctions.negate(predicate_name)

                    x = ""
                    for e in element[element.find("(") + 1:element.rfind(")")].split(","):
                        x = x + str(e) + ','
                    x = x[:-1]
                    predicate = predicate_name + "(" + x + ")"

                    list_of_difference_red[i].append(predicate)
        if deep_investigation:
            flat_list_white = diagnosis.converter(list_of_difference_white)
            flat_prev_white = helperFunctions.add_point(diagnosis.converter(tmp_prev_white))
                
            if set(helperFunctions.add_point(flat_list_white)).difference(set(list_of_added_knowledge)).difference(set(flat_prev_white)):
                
                first = f"Selection:\n{facet}\n"
                
                second = "Dependency:\n"
                for element in list(set(helperFunctions.add_point(flat_list_white)).difference(set(list_of_added_knowledge))):
                    if element not in helperFunctions.add_point(flat_prev_white):
                        if "alpha" in element:
                            second += element[:element.index('(')] + "\n"
                        if "remove" in element:
                            e_id = element[element.index('(')+1:element.index(')')]
                            if "not" in element:
                                second += f"alpha{e_id}\n"
                            else:
                                second += f"not alpha{e_id}\n"
                save_text = first + second
                save_deep_investigation(save_text)
                print_red_blue_white()
            else:
                print_red_blue_white()
        print_red_blue_white()
        tmp_prev_white = list(list_of_difference_white)


def print_red_blue_white():
    global justifications_program_path

    one = True
    two = True
    three = True        

    disp_file = open("facets_options.txt", "w")
    if len(set(tuple(i) for i in list_of_difference_red).intersection(set(tuple(i) for i in list_of_difference_red))) > 1:
        disp_file.write("Unavailable facets\n")
        to_print = get_facets_to_print(list_of_difference_red)   
        for element in to_print:
            disp_file.write(element)
    else:
        one = False
    if one:
        if len(set(tuple(i) for i in list_of_difference_blue).intersection(set(tuple(i) for i in list_of_difference_blue))) > 1:
            disp_file.write("Available facets\n")
            to_print = get_facets_to_print(list_of_difference_blue)   
            for element in to_print:
                disp_file.write(element)
        else:
            two = False

        if len(set(tuple(i) for i in list_of_difference_white).intersection(set(tuple(i) for i in list_of_difference_white))) > 1:
            disp_file.write("Chosen facets\n")
            to_print = get_facets_to_print(list_of_difference_white)   
            for element in to_print:
                disp_file.write(element)
        else:
            three = False
        disp_file.close()
    if not one:  # and not two and not three and list_of_added_knowledge:
        translator(justifications_program_path, True)  

def initial_display(clingo_return):
    if clingo_return == "SAT":
        global conf_pr, allowed_entries
        to_print = []
        if not list_of_added_knowledge:
            for element in list_of_answer_sets[len(list_of_answer_sets) - 1]:
                if "remove" in element.p_name:
                    for arguments in element.p_elements:
                        predicate = element.p_name + "(" + arguments + ")."
                        if predicate not in first_ever:
                            to_print.append(predicate)
            disp_file = open("facets_options.txt", "w")
            disp_file.write("Available facets\n")
            if to_print:
                allowed_entries = set(to_print)        
                for f in to_print:
                    disp_file.write(str(f))
                    if f != to_print[-1]:
                        disp_file.write("\n")
                disp_file.close()
            else:
                disp_file.write("No facets available.")
                disp_file.close()         

    else:
        return clingo_return


class Predicate:
    """ 
    predicate and its arguments
    """
    def __init__(self, predicate_name):
        """ 
        Initialise an element of type Predicate
        :param predicate_name: 
        """
        self.p_name = predicate_name
        self.p_elements = []

    @staticmethod
    def add_elements(predicate, predicate_element):
        """ 
        fill the elements associated to the predicate
        :param predicate: 
        :param predicate_element: 
        :return: 
        """

        x = ""
        for part in predicate_element:
            x = x + str(part) + ',' 
        x = x[:-1]
        predicate_element = x 
        if not (predicate_element in predicate.p_elements):
            predicate.p_elements.append(predicate_element)

    def __getstate__(self):
        """Prepare the object for pickling."""
        state = self.__dict__.copy()  # Copy the current state
        return state  # Return the state as a dict

    def __setstate__(self, state):
        """Restore the object from pickling."""
        self.__dict__.update(state)

class PredicateIndex:
    """ 
    name of the predicate plus an index to indicate which list corresponds to that predicate in the model (the model is list of lists)
    """
    def __init__(self, predicate_name, ind):
        """ 
        initialise an element of type PredicateIndex
        :param predicate_name: 
        :param ind: 
        """
        self.p_name = predicate_name
        self.p_index = ind 


def store_globals():
    with open("pyglobals.pk1", 'wb') as f:
            pickle.dump({
                'first_ever': first_ever,
                'first_answer_set_ever': first_answer_set_ever,
                # 'last_answer_set': last_answer_set,
                'first_list_of_predicates': first_list_of_predicates,
                'list_of_answer_sets': list_of_answer_sets,
                'list_of_predicates': list_of_predicates,
                'list_of_difference_blue' : list_of_difference_blue,
                'list_of_difference_red' : list_of_difference_red,
                'list_of_difference_white' : list_of_difference_white,
                'tmp_prev_red' : tmp_prev_red,
                'tmp_prev_white' : tmp_prev_white,
                'allowed_entries' : allowed_entries,
                'list_of_predicates_not_to_negate' : list_of_predicates_not_to_negate
            }, f)


def fetch_globals():
    global first_ever, first_answer_set_ever, first_list_of_predicates, list_of_answer_sets, list_of_predicates,\
    list_of_difference_blue, list_of_difference_red, list_of_difference_white, tmp_prev_red, tmp_prev_white, allowed_entries, list_of_predicates_not_to_negate
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
        list_of_predicates_not_to_negate = data['list_of_predicates_not_to_negate']

def get_facets_to_print(list_of_facets):
    to_print = []
    for lst in list_of_facets:
        for element in lst:
            if "remove" in element:  
                e_id = element[element.index('(')+1:element.index(')')] 
                if element[:4] == "not ":
                    to_print.append(f"({e_id})\n")
                else:
                    to_print.append(f"not ({e_id})\n")
            if "alpha" in element:
                e_id = element[element.index("alpha")+len("alpha"):element.index("()")] 
                if "not" in element:
                    to_print.append(f"not ({e_id})\n")
                else:
                    to_print.append(f"({e_id})\n")

    return set(to_print)

def save_deep_investigation(save_text):
    with open("deep_investigation.txt", "w") as f:
        f.write(save_text)
