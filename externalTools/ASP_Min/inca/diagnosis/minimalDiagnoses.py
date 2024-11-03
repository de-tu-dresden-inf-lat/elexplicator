#from scipy.weave.catalog import intermediate_dir

from diagnosis import diagnosis
from diagnosis import correctionset
import os
import clingo
import ast
import operator
import dill as pickle


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


minimal_conflict_sets_asp = []

def get_all_minimal_diagnoses(max_index, justifications_program_path, mDsID, min_diag, facet_diag, first_run):
    """
    Generate all optimal classical repairs for making "not statement()" a valid option
    :param max_index:
    :param justifications_program_path:
    :return:
    """
    global all_optimal_classical_repairs, optimal_classical_repairs_file_path, intermediate_optimal_classical_repairs, first_ever, \
    first_answer_set_ever, last_answer_set, init_first_answer_set, first_list_of_predicates, list_of_answer_sets, list_of_predicates,\
    list_of_difference_blue, list_of_difference_red, list_of_difference_white, tmp_prev_red, allowed_entries
    all_optimal_classical_repairs = []
    intermediate_optimal_classical_repairs = []
    # path = justifications_program_path[:justifications_program_path.rfind(os.sep) + 1]
    # optimal_classical_repairs_file_path = path + mDsID
    optimal_classical_repairs_file_path =  mDsID

    init_first_answer_set = first_run

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

    info_dict = {}
    # if facet_diag and not first_run and os.path.exists("info_file.txt"):    
    #     file = open("info_file.txt", 'r')
    #     lines = (file.readlines())
    #     for line in lines:
    #         line = line.strip()            
    #         info_dict[line.split(":")[0]] = line.split(":")[1].strip()
    #     file.close()   
    #     first_ever = info_dict["first_ever"] 
    #     print("first_ever:", first_ever)
    #     first_answer_set_ever = ast.literal_eval(info_dict["first_answer_set_ever"])
    #     print("first_answer_set_ever:", first_answer_set_ever)
    #     first_answer_set_ever_tmp=[]
    #     for element in first_answer_set_ever:
    #         pred_name = element[:element.index('(')]
    #         predicate = Predicate(pred_name)
    #         pred_arg = element[element.index('(')+1:element.index(')')]
    #         Predicate.add_elements(predicate, pred_arg)
    #         first_answer_set_ever_tmp.append(predicate)
    #     last_answer_set = info_dict['last_answer_set']
    #     # print("answerset:", first_answer_set_ever_tmp)
    #     first_answer_set_ever = first_answer_set_ever_tmp
    #     first_list_of_predicates = ast.literal_eval(info_dict['first_list_of_predicates'])
    #     first_list_of_preds_tmp=[]
    #     for element in first_list_of_predicates:
    #         pred_name = element[:element.index('(')]
    #         predicate = Predicate(pred_name)
    #         pred_arg = element[element.index('(')+1:element.index(')')]
    #         Predicate.add_elements(predicate, pred_arg)
    #         first_list_of_preds_tmp.append(predicate)
    #     first_list_of_predicates = first_list_of_preds_tmp

    if facet_diag and not first_run and os.path.exists("pyglobals.pk1"):
        get_added_knowledge_function()
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
            allowed_entries = data['allowed_entries']
    if facet_diag:
        res = translator(justifications_program_path, False)
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
                'allowed_entries' : allowed_entries
            }, f)

    return compute_all_optimal_classical_repairs(justifications_program_path, max_index+1)

def get_added_knowledge_function():
    global list_of_added_knowledge
    with open("added_knowledge.txt", "r") as f:
        lines = f.readlines()
        for line in lines:
            list_of_added_knowledge.append(line)

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
    print("init_first_answer_set:", init_first_answer_set)
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
            for answer in first_answer_set_ever:
                for args in answer.p_elements:
                    answer_set_li.append(answer.p_name + "(" + args + ")")
            print("first_answer_set_ever:", answer_set_li)

        if len(first_answer_set_ever) != 0 and len(list_of_answer_sets) != 0 and not init_first_answer_set:
            if len(list_of_answer_sets[0]) != 0:
                compare(first_answer_set_ever, list_of_answer_sets[len(list_of_answer_sets) - 1], False)
                print("blue:", list_of_difference_blue)
                print("red:", list_of_difference_red)
                print("white:", list_of_difference_white)

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

                predicate = Predicate(negate(tmp + atom.name))
                Predicate.add_elements(predicate, atom.arguments)

                list_of_predicates.append(predicate)
        else:
            for i in range(len(list_of_predicates)):
                if list_of_predicates[i].p_name == atom.name:
                    predicate = list_of_predicates[i]
                    Predicate.add_elements(predicate, atom.arguments)
                    if predicate.p_name + "(" + predicate.p_elements[len(predicate.p_elements) - 1] + ")" not in first_ever:
                        name_in_list = [x for x in list_of_predicates if x.p_name == negate(atom.name)]
                        if len(name_in_list) > 0:
                            predicate = name_in_list[0]
                        else:
                            predicate = Predicate(negate(tmp + atom.name))
                            list_of_predicates.append(predicate)
                        Predicate.add_elements(predicate, atom.arguments)
                    found = True
                    break

            if not found:
                predicate = Predicate(tmp + atom.name)
                Predicate.add_elements(predicate, atom.arguments)
                if predicate.p_name + "(" + predicate.p_elements[len(predicate.p_elements) - 1] + ")" not in first_ever:

                    list_of_predicates.append(predicate)

                    predicate = Predicate(negate(tmp + atom.name))
                    Predicate.add_elements(predicate, atom.arguments)

                    list_of_predicates.append(predicate)
            found = False
    list_of_answer_sets.append(list_of_predicates)
    li_preds = []
    for answer in list_of_answer_sets[0]:
        for args in answer.p_elements:
            li_preds.append(answer.p_name + "(" + args + ")")
    print('first_list_of_predicates:', li_preds)
    return None

def negate(atom):
    """
    this function will negate the provided atom
    :param atom: 
    :return: 
    """
    if atom[:4] == "not ":
        atom = atom[4:]
    else:
        atom = "not " + atom
    return atom

def negate_set(answer_set):
    negated_answer_set = []
    for atom in answer_set:
        if atom[:4] == "not ":
            atom = atom[4:]
        else:
            atom = "not " + atom
        negated_answer_set.append(atom)
    return negated_answer_set

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
            print("first_ever:", first_ever)


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
    print("last_answer_set:", last_answer_set)

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
        asp_file_name, union_of_answers_without_c, tmp_prev_white

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
                    tmp.append(negate(list_of_difference_blue[ind][e]))
                for e in tmp:
                    list_of_difference_blue[i].append(e)

                tmp_blue = []
                p_name = negate(new_model[i].p_name)
                ind = [x for x in list_of_indices if x.p_name == p_name][0].p_index

                for argument in new_model[ind].p_elements:
                    tmp_blue.append(new_model[ind].p_name + "(" + argument + ")")

                for_sure_blue = list_of_difference_white[ind]

                for element in for_sure_blue:

                    predicate_name = element[:element.index("(")]
                    predicate_name = negate(predicate_name)

                    x = ""
                    for e in element[element.find("(") + 1:element.rfind(")")].split(","):
                        x = x + str(e) + ','
                    x = x[:-1]
                    predicate = predicate_name + "(" + x + ")"

                    list_of_difference_red[i].append(predicate)

        # if deep_investigation:
            # start = '\033[95m'
            # end = '\033[0m'
            # flat_list_white = diagnosis.converter(list_of_difference_white)
            # flat_prev_white = add_point(diagnosis.converter(tmp_prev_white))
            # if set(add_point(flat_list_white)).difference(set(list_of_added_knowledge)).difference(set(flat_prev_white)):
            #     first = "The selection of "
            #     for element in input_list:
                    # first += start + element[:len(element)-1] + end + ", "
            #     first = first[:len(first)-2]
            #     second = " also requires the selection of "
            #     for element in list(set(add_point(flat_list_white)).difference(set(list_of_added_knowledge))):
            #         if element not in add_point(flat_prev_white):
            #             second += start + element[:len(element)-1] + end + ", "
            #     second = second[:len(second)-2]
            #     display_text = first + second + "\ndo you want to continue?(y/n)\n"
            #     option = input(display_text)
            #     if option.lower() == 'y':
            #         print_red_blue_white()
            #     else:
            #         del_function(asp_file_name, input_list, False)
            #         input_list = []
            #         translator(asp_file_name, True)
            # else:
            #     print_red_blue_white()
        print_red_blue_white()
        tmp_prev_white = list(list_of_difference_white)


def print_red_blue_white():
    global justifications_program_path
    """
    
    :return: 
    """
    one = True
    two = True
    three = True        

    disp_file = open("facets_options.txt", "w")
    if len(set(tuple(i) for i in list_of_difference_red).intersection(set(tuple(i) for i in list_of_difference_red))) > 1:
        print("red", list_of_difference_red)
        disp_file.write("Unavailable facets\n")
        for lst in list_of_difference_red:
            for element in lst:
                if "remove" in element:    
                    disp_file.write(element+"\n")
        # print_options(["Unavailable Facets:"], '\033[1;33m')
        # for lst in list_of_difference_red:
        #     print_options(lst, '\033[1;31m')
    else:
        one = False
    if one:
        if len(set(tuple(i) for i in list_of_difference_blue).intersection(set(tuple(i) for i in list_of_difference_blue))) > 1:
            print("blue:", list_of_difference_blue)
            disp_file.write("Available facets\n")
            for lst in list_of_difference_blue:
                for element in lst:
                    if "remove" in element:    
                        disp_file.write(element+"\n")
        else:
            two = False

        if len(set(tuple(i) for i in list_of_difference_white).intersection(set(tuple(i) for i in list_of_difference_white))) > 1:
            # print_options(["Chosen Facets:"], '\033[1;33m')
            # for lst in list_of_difference_white:
            #     print_options(lst)
            disp_file.write("Chosen facets\n")
            for lst in list_of_difference_white:
                for element in lst:
                    if "alpha" in element:  
                        element_id = element[element.index("alpha")+len("alpha"):element.index("()")] 
                        if "not" in element:
                            disp_file.write("not alpha("+element_id+")\n")
                        else:
                            disp_file.write("alpha("+element_id+")")
            print('list_of_difference_white:', list_of_difference_white)
        else:
            three = False
        disp_file.close()
    if not one:  # and not two and not three and list_of_added_knowledge:
        translator(justifications_program_path, True)


def add_point(some_list):
    """
    add a full stop to the end of every predicate
    :param some_list: 
    :return: 
    """
    ret = []
    for i in some_list:
        if len(i) > 0:
            if i[len(i) - 1] != ".":
                i += "."
            ret.append(i)
    return ret


    

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
            
            
            # if to_print:
            #     allowed_entries = set(to_print)
            #     print('\033[1;33m' + "\nThe following facets are available:\n" + '\033[0m')
            #     print_options(to_print)
            # else:
            #     print('\033[1;33m' + "\nThe provided problem has no facets!\n" + '\033[0m')
            #     conf_pr = False
            # for atom in list_of_answer_sets[len(list_of_answer_sets) - 1]:
            #     if "remove(" in str(atom) and atom not in first_ever:
            #         if "not " == str(atom)[:4]:
            #             to_print.append("not " + str(atom)[str(atom).index("(")+1:str(atom).index(")")])
            #         else:
            #             to_print.append(str(atom)[str(atom).index("(")+1:str(atom).index(")")])

    else:
        return clingo_return
        # print(clingo_return)


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

def reactivate_function(input_text):
    global input_list
    get_added_knowledge_function()
    input_list = input_text.split("/")
    for e in input_list:
        atomId = facet.split("alpha")[1]
        if "not" in atomId:
            atomId = 'not '+atomId
        input_list_tmp.append(atomId)
    input_list = [e for e in input_list_tmp if e]
    input_list = add_point(handle_input_negation(input_list))
    input_list = [e for e in input_list if e in allowed_entries]
    
    if input_list:
        if input_list[0] in add_point(diagnosis.converter(list_of_difference_red)):
            if not diagnosis.simple_inconsistency_chech(list_of_added_knowledge, input_list[0]):
                diagnosis.create_original(list_of_added_knowledge, justifications_program_path)
                correction_sets = correctionset.cs_generator_2(list_of_added_knowledge, input_list[0], asp_file_name)
                print_Correction_Sets(correction_sets)
            else:
                print("Because you have already selected "+negate(input))
    
def print_Correction_Sets(to_keep):
    """
    print the reasons in a form of possible deletions
    :param reasons: 
    :return: 
    """
    global input_list
    reasons = []
    start = '\033[95m'
    under_line = '\033[4m'
    end = '\033[0m'
    first = "To be able to select "
    for e in input_list:
        first += e[:len(e)-1] + ", "
    first = first[:len(first)-2] + " you have to remove "
    for l in to_keep:
        tmp = []
        for ind in [i for i in range(0, len(list_of_added_knowledge)) if i in l]:
            tmp.append(list_of_added_knowledge[ind])
        reasons.append(tmp)
    second = ""
    intersection = diagnosis.pruner_2(reasons)
    if len(intersection) != len(reasons[0]):
        for i in intersection:
            second += start + i[:len(i)-1] + end + " " + under_line + "and" + end + " "
        if len(reasons) > 2:
            second += "one of the following combinations\n\t"
        else:
            second += "\n\t"
        for reason in reasons:
            for r in list(set(reason).difference(set(intersection))):
                second += start + r[:len(r)-1] + end + ", "
            second = second[:len(second) - 2]
            second += "\n" + under_line + "or" + end + "\n\t"
        second = second[:second.rfind("\n", 0, second.rfind("\n")) + 1]
    else:
        if len(reasons) > 2:
            first += " one of the following combinations\n\t"
        else:
            first += "\n\t"
        for reason in reasons:
            for r in reason:
                second += start + r[:len(r) - 1] + end + ", "
            second = second[:len(second) - 2]
            second += "\n" + under_line + "or" + end + "\n\t"
        second = second[:second.rfind("\n", 0, second.rfind("\n")) + 1]

    print(first + second)

def impact_function(input_text):
    global input_list
    get_added_knowledge_function()
    input_list= input_text.split("/")
    input_list_tmp =[]
    for e in input_list:
        atomId = facet.split("alpha")[1]
        if "not" in atomId:
            atomId = 'not '+atomId
        input_list_tmp.append(atomId)
    input_list = [e for e in input_list_tmp if e]
    input_list = add_point(handle_input_negation(input_list))
    if not what_if_delete():
        print("This deletion will not affect the rest of the chosen options")

def what_if_delete():
    """
    this function returns the difference between a program befor some delletion and after it
    :return: 
    """
    global list_of_added_knowledge, asp_file_name, input_list, what
    start = '\033[95m'
    end = '\033[0m'
    diagnosis.create_original(list_of_added_knowledge, asp_file_name)
    tmp_asp_path = asp_file_name[:asp_file_name.rfind(os.sep) + 1]
    tmp_asp_file = tmp_asp_path + "what_if.txt"
    if os.path.exists(tmp_asp_file):
        os.remove(tmp_asp_file)

    copyfile(tmp_asp_path + "original_asp_program.txt", tmp_asp_file)

    with open(tmp_asp_file, "a") as tmp:
        for e in add_point([element for element in list_of_added_knowledge if element not in add_point(input_list)]):
            tmp.write(":- " + negate(e) + "\n")
    tmp.close()

    args = ['--enum-mode=cautious']
    prg = clingo.Control(args)
    try:
        prg.load(tmp_asp_file)
    except RuntimeError:
        return None
    prg.ground([("base", []), ("parts", [])])
    prg.solve(on_model=model_what_if)

    first = "Removing "
    for e in input_list:
        first += start + e[:len(e) - 1] + end + ", "
    first = first[:len(first) - 2]
    second = " will cause the deletion of "
    third = ""
    for e in list(set(add_point(diagnosis.converter(list_of_difference_white))).difference(add_point(what_if_white))):
        if e not in list_of_added_knowledge:
            third += start + e[:len(e) - 1] + end + ", "
    third = third[:len(third) - 2]
    if third:
        print(first + second + third)
        return True
    else:
        return False

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
            tmp = "-"
        else:
            tmp = ""
        for argument in [atom.arguments]:
            x = ""
            for part in argument:
                x = x + str(part) + ','
            x = x[:-1]
            what_if_white.append(tmp + atom.name + "(" + x + ")")
