import re
import sys

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

def transform_facets(list_of_facets):
    # transform atoms of lp to identifiers form
    transformed_facets = []
    for element in list_of_facets:
        if "alpha" in element:
            transformed_facets.append(element[:element.index('(')])
        if "remove" in element:
            e_id = element[element.index('(')+1:element.index(')')]
            if "not" in element:
                transformed_facets.append(f"alpha{e_id}")
            else:
                transformed_facets.append(f"not alpha{e_id}")
    return list(set(transformed_facets))

def transform_alpha_to_remove(atom):
    # transform from atom identifiers "alpha..." to remove(...) atoms
    try:
        atom_id = atom[atom.index("alpha")+len("alpha"):atom.index(".")] 
    except ValueError or IndexError:
        sys.exit(2)
    if atom[:4]=="not ":
        ret_atom = f"remove({atom_id})."
    else:
        ret_atom = f"not remove({atom_id})."
    return ret_atom

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
