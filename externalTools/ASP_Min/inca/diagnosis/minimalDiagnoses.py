#from scipy.weave.catalog import intermediate_dir

import diagnosis
import os
import clingo

list_of_added_knowledge = []

def get_all_minimal_diagnoses(max_index, justifications_program_path, mDsID, min_diag, facet_diag, first_run):
    """
    Generate all optimal classical repairs for making "not statement()" a valid option
    :param max_index:
    :param justifications_program_path:
    :return:
    """
    global all_optimal_classical_repairs, optimal_classical_repairs_file_path, intermediate_optimal_classical_repairs

    
    all_optimal_classical_repairs = []
    intermediate_optimal_classical_repairs = []
    # path = justifications_program_path[:justifications_program_path.rfind(os.sep) + 1]
    # optimal_classical_repairs_file_path = path + mDsID
    optimal_classical_repairs_file_path =  mDsID

    if os.path.exists(optimal_classical_repairs_file_path):
        os.remove(optimal_classical_repairs_file_path)

    if min_diag or first_run:
        program = open(justifications_program_path, "a")

        program.write("\n:- not remove(_).\n")
        for i in range(0, max_index+1):
            program.write("remove(" + str(i) + "):- " + "not alpha"+str(i) + ".\n")

        program.write(":- statement.\n")
        program.close()

    if facet_diag:
        translator(justifications_program_path, optimal_classical_repairs_file_path, True)

    return compute_all_optimal_classical_repairs(justifications_program_path, max_index+1)


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
        print(str(atom))
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


def translator(asp_file, out_file, first_run):
    global list_of_answer_sets, first_answer_set_ever, init_first_answer_set
    init_first_answer_set = first_run
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
            first_answer_set_ever = list_of_answer_sets[len(list_of_answer_sets) - 1]

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
            tmp = "not"
        else:
            tmp = ""
        predicate = tmp + str(atom)
        if not list_of_predicates:          

            if predicate not in first_ever:

                list_of_predicates.append(predicate)
                list_of_predicates.append(negate(predicate))
        else:
            for i in range(len(list_of_predicates)):
                if list_of_predicates[i] == predicate:
                    if predicate not in first_ever:
                        list_of_predicates.append(negate(predicate))
                    found = True
                    break

            if not found:
                if predicate not in first_ever:

                    list_of_predicates.append(predicate)
                    list_of_predicates.append(negate(predicate))
            found = False
    list_of_answer_sets.append(list_of_predicates)
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
    print("create_last_answer_set")
    assert isinstance(model, clingo.Model)
    global last_answer_set
    last_answer_set = [model.symbols(atoms=True)]
    print("create_last_answer_set")

def compare(old_model, new_model, deep_investigation):
    """
    will be different from inca.py compare function because the axioms are different
    """
    global tmp_prev_red, tmp_prev_blue, list_of_difference_red, list_of_difference_blue, list_of_indices, list_of_difference_white, input_list,\
        asp_file_name, union_of_answers_without_c, tmp_prev_white

def initial_display(clingo_return):
    if clingo_return == "SAT":
        global conf_pr, allowed_entries
        to_print = []
        if not list_of_added_knowledge:
            for atom in list_of_answer_sets[len(list_of_answer_sets) - 1]:
                if "remove(" in str(atom) and atom not in first_ever:
                    if "not " == str(atom)[:4]:
                        to_print.append("not " + str(atom)[str(atom).index("(")+1:str(atom).index(")")])
                    else:
                        to_print.append(str(atom)[str(atom).index("(")+1:str(atom).index(")")])
            disp_file = open("facets_options.txt", "w")
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
        print(clingo_return)
