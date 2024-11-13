from diagnosis import diagnosis, correctionset, minimalDiagnoses, impactComputation, helperFunctions
import dill as pickle
from diagnosis.diagnosis import copyfile, clingo, negate
import os
import sys

list_of_added_knowledge = []
justifications_program_path = ''
list_of_difference_red = []
def reactivate_function(asp_file_name, input_text):
    global input_list_original, input_list, list_of_added_knowledge, justifications_program_path
    justifications_program_path = asp_file_name
    minimalDiagnoses.fetch_globals()
    list_of_difference_red = minimalDiagnoses.list_of_difference_red
    minimalDiagnoses.get_added_knowledge_function()
    list_of_added_knowledge = minimalDiagnoses.list_of_added_knowledge
    input_list_original = input_text.split("/")
    input_list_tmp = []
    for e in input_list_original:
        atomId = e.split("alpha")[1]
        if "not" in e:
            atom1 = f'not alpha{atomId}()'
            atom2 = f"remove({atomId})"
        else:
            atom1 = f'alpha{atomId}()'
            atom2 = f"not remove({atomId})"
        input_list_tmp.append(atom1)
        input_list_tmp.append(atom2)
    input_list = [e for e in input_list_tmp if e]
    input_list = helperFunctions.add_point(helperFunctions.handle_input_negation(input_list))
    input_list = [e for e in input_list if e in minimalDiagnoses.allowed_entries]
    
    if input_list:
        if input_list[0] in helperFunctions.add_point(diagnosis.converter(list_of_difference_red)):
            if not diagnosis.simple_inconsistency_chech(list_of_added_knowledge, input_list[0]):
                diagnosis.create_original(list_of_added_knowledge, justifications_program_path)
                correction_sets = cs_generator_2(list_of_added_knowledge, input_list[0], justifications_program_path)
                print_Correction_Sets(correction_sets)
            else:
                # print("Because you have already selected "+minimalDiagnoses.negate(input_list[0]))
                save_correction_set(helperFunctions.transform_facets([helperFunctions.negate(input_list[0])]), [])
        else:
            sys.exit(2)
            
    
def print_Correction_Sets(to_keep):
    """
    print the reasons in a form of possible deletions
    :param reasons: 
    :return: 
    """
    global input_list_original
    reasons = []
    for l in to_keep:
        tmp = []
        for ind in [i for i in range(0, len(list_of_added_knowledge)) if i in l]:
            tmp.append(list_of_added_knowledge[ind])
        reasons.append(tmp)
    intersection = diagnosis.pruner_2(reasons)
    intersection = helperFunctions.transform_facets(intersection)
    reasons_tmp = []
    for r in reasons:
        reasons_tmp.append(helperFunctions.transform_facets(r))
    reasons = reasons_tmp
    intersect_list = []
    combinations_list = []
    if len(intersection) != len(reasons[0]):       
        
        for i in intersection:
            intersect_list.append(i)
        
        for reason in reasons:
            combination = []
            for r in list(set(reason).difference(set(intersection))):
                combination.append(r)
            combinations_list.append(combination)
    else:    
        for reason in reasons:
            combination = []
            for r in reason:
                combination.append(r)
            combinations_list.append(combination)
    save_correction_set(intersect_list, combinations_list)

def save_correction_set(intersection_list, combinations_list):
    with open("corrections.txt", "w") as f:
        f.write("To reactivate:\n")
        for input in input_list_original:
            f.write(input+"\n")
        if intersection_list:
            if len(intersection_list) > 1:
                f.write("Remove all:\n")
            else:
                f.write("Remove:\n")
            for i in intersection_list:
                f.write(i+"\n")
        if combinations_list:
            for combination in combinations_list:
                if len(combination) > 1:
                    f.write("Remove combination of:\n")
                else:
                    f.write("Remove:\n")
                for c in combination:
                    f.write(c + "\n")
                if combinations_list.index(combination) != len(combinations_list)-1:
                    f.write("OR\n")
    

def cs_generator_2(original_list_of_options, problematic, asp_path):
    """
    generate some conflict sets (size 100)
    :param original_list_of_options:
    :param problematic:
    :param asp_path:
    :return:
    """
    global all_minimal_conflict_sets_asp, tmp_asp_path, new_corrections

    all_minimal_conflict_sets_asp = []
    new_corrections = []
    tmp_asp_path = asp_path[:asp_path.rfind(os.sep) + 1]
    tmp_asp_file = tmp_asp_path + "conflict_tester.txt"

    if os.path.exists(tmp_asp_file):
        os.remove(tmp_asp_file)

    diagnosis.copyfile(tmp_asp_path + "original_asp_program.txt", tmp_asp_file)
    tmp_asp_file = open(tmp_asp_path + "conflict_tester.txt", "a")

    tmp_asp_file.write(":- not rem(_).\n")
    for e in original_list_of_options:
        tmp_asp_file.write("rem(" + str(original_list_of_options.index(e)) + "):- " + negate(e[:len(e)-1]) + ".\n")
    if len(problematic) > 0:
        if problematic[len(problematic) - 1] != ".":
            problematic += "."
    tmp_asp_file.write(":- " + negate(problematic) + "\n")
    tmp_asp_file.close()

    return check_unsat_ram_2(tmp_asp_path + "conflict_tester.txt", len(original_list_of_options), original_list_of_options)


def check_unsat_ram_2(asp_path, len_original, original_list_of_options):
    """

    :param asp_path:
    :param len_original:
    :return:
    """

    global new_corrections

    for i in range(1, len_original+1):
        # args = ['--configuration=handy', '-t 4', '--models=0']
        args = ['--models=0', '-t 4']
        prg = clingo.Control(args)
        try:
            prg.load(asp_path)
        except RuntimeError as rte:
            print(rte)
            return "Parsing Problem"
        prg.ground([("base", []), ("parts", [])])
        tester = ":- not " + str(i) + "#count{X:rem(X)}" + str(i) + "."
        prg.add("", [], tester)
        prg.ground([("", [])])
        # ret = ""
        try:
            ret = str(prg.solve())
        except RuntimeError:
            ret = "UNSAT"
        if ret == "SAT":
            str(prg.solve(on_model=optimal_model_2))
            # break
            tmp_asp_file = open(tmp_asp_path + "conflict_tester.txt", "a")
            for correction_set in index_to_integrity_constraint(new_corrections, original_list_of_options):
                tmp_asp_file.write(correction_set)
            tmp_asp_file.close()
            new_corrections = []

            args = ['--models=1']
            prg = clingo.Control(args)
            prg.load(asp_path)
            prg.ground([("base", []), ("parts", [])])
            try:
                ret2 = str(prg.solve())
            except RuntimeError:
                ret2 = "UNSAT"
            if ret2 == "SAT":
                print("more to go")
            else:
                print("no more")
                break

    print('Total number of correction sets = ' + str(len(all_minimal_conflict_sets_asp)))
    return all_minimal_conflict_sets_asp


def optimal_model_2(model):
    global all_minimal_conflict_sets_asp, new_corrections
    tmp = []

    for atom in model.symbols(shown=True):
        if "rem(" in str(atom):
            tmp.append(int(str(atom)[str(atom).index("(")+1:str(atom).index(")")]))
    if tmp not in all_minimal_conflict_sets_asp:
        all_minimal_conflict_sets_asp.append(tmp)
        new_corrections.append(tmp)


def index_to_integrity_constraint(correction_sets, original_list_of_options):
    new_integrity_constraints = []
    for l in correction_sets:
        integrity_constraint = ""
        for ind in [i for i in range(0, len(original_list_of_options)) if i in l]:
            integrity_constraint = negate(original_list_of_options[ind][:len(original_list_of_options[ind])-1]) + "," + integrity_constraint
        new_integrity_constraints.append(":- " + integrity_constraint[0:len(integrity_constraint) - 1] + ".\n")
    return new_integrity_constraints
