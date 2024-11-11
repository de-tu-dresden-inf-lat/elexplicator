from diagnosis import helperFunctions, minimalDiagnoses
def del_function(to_delete_from_file_name, input_text, print_message=True):
    """
    This function deletes from the provided asp file every thing that is given in the input list 
    :param to_delete_from_file_name: 
    :param to_delete_list:
    :param print_message:
    :return: 
    """
    global list_of_added_knowledge, list_of_difference_red, list_of_predicates_not_to_negate, tmp_prev_white
    get_global_variables()
    
    to_delete_list = get_del_list(input_text)
    to_delete_list = [":- " + helperFunctions.negate(transform(x+'.')) for x in to_delete_list]
    print(to_delete_list)
    del_counter = 0
    fs = open(to_delete_from_file_name, "r")
    lines = fs.readlines()
    fs.close()
    fs = open(to_delete_from_file_name, "w")
    for line in lines:
        found = False
        line = line.strip()
        for inpt in helperFunctions.handle_input_negation(to_delete_list):
            
            if line == inpt:
                print("line: ", line, "input: ", inpt)
                found = True
                remove_from_knowledge(inpt)
                del_counter += 1
                break
        if not found and line:
            fs.write(line + "\n")
    fs.close()
    print("list of added knowledge: ", list_of_added_knowledge)
    # essential reset part for list_of_difference_red
    if len(list_of_added_knowledge) == 0:
        # set init_first_answerset as True else False
        for i in range(len(list_of_difference_red)):
            list_of_difference_red[i] = []
        # reset list of cautious
        list_of_predicates_not_to_negate = []
        # rest previous white
        tmp_prev_white = []
        logs = []
    if print_message:
        if del_counter > 0:
            print("deleted!")
        else:
            print("nothing has been deleted!")

    update_globals()

def del_all(to_delete_from_file_name):
    global list_of_added_knowledge
    get_global_variables()
    to_delete = "/".join([i[:i.index("()")]+'.' for i in list_of_added_knowledge if "alpha" in i])
    del_function(to_delete_from_file_name, to_delete)

def get_del_list(input_text):
    input_list = input_text.split("/")
    input_list = [e for e in input_list if e]
    input_list = helperFunctions.handle_input_negation(input_list)
    return input_list

def transform(atom):
    atom_id = atom[atom.index("alpha")+len("alpha"):atom.index(".")] 
    if atom[:4]=="not ":
        ret_atom = f"remove({atom_id})."
    else:
        ret_atom = f"not remove({atom_id})."
    return ret_atom

def remove_from_knowledge(line):
    global list_of_added_knowledge
    atom = line[3:]
    print("atom:", atom)
    to_remove = []
    for x in list_of_added_knowledge:
        if x == helperFunctions.negate(atom):
            to_remove.append(x)
        elif "alpha" in x:
            if transform(x[:x.index('()')]+'.') == helperFunctions.negate(atom):
                to_remove.append(x)
    list_of_added_knowledge = [x for x in list_of_added_knowledge if x not in to_remove]
    with open("added_knowledge.txt", "w") as f:
        for i in list_of_added_knowledge:
            f.write(i + '\n')
        

def get_global_variables():
    global list_of_added_knowledge, list_of_difference_red, list_of_predicates_not_to_negate, tmp_prev_white
    minimalDiagnoses.fetch_globals()
    list_of_difference_red = minimalDiagnoses.list_of_difference_red
    list_of_predicates_not_to_negate = minimalDiagnoses.list_of_predicates_not_to_negate
    tmp_prev_white = minimalDiagnoses.tmp_prev_white
    minimalDiagnoses.get_added_knowledge_function()
    list_of_added_knowledge = minimalDiagnoses.list_of_added_knowledge

def update_globals():
    global list_of_added_knowledge, list_of_difference_red, list_of_predicates_not_to_negate, tmp_prev_white
    minimalDiagnoses.list_of_difference_red = list_of_difference_red
    minimalDiagnoses.list_of_predicates_not_to_negate = list_of_predicates_not_to_negate
    minimalDiagnoses.tmp_prev_white = tmp_prev_white
    minimalDiagnoses.store_globals()

