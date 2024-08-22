#from scipy.weave.catalog import intermediate_dir

import diagnosis
import os
import clingo


def get_all_minimal_diagnoses(max_index, justifications_program_path, mDsID):
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

    program = open(justifications_program_path, "a")

    program.write("\n:- not remove(_).\n")
    for i in range(0, max_index+1):
        program.write("remove(" + str(i) + "):- " + "not alpha"+str(i) + ".\n")

    program.write(":- statement.\n")
    program.close()

    return compute_all_optimal_classical_repairs(justifications_program_path, max_index+1)


def compute_all_optimal_classical_repairs(program_path, len_original):
    """

    :param program_path:
    :param len_original:
    :return:
    """
    global intermediate_optimal_classical_repairs

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
