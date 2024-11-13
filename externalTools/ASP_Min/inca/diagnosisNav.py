from argparse import ArgumentParser
import os
from diagnosis import minimalDiagnoses, impactComputation, reactivateComputation, deletion, helperFunctions, diagnosis

def main():
    parser = ArgumentParser()

    parser.add_argument("-path", dest="filePath", required=True,
                        help="input file that contains the logic program", metavar="FILE",
                        type=lambda x: is_valid_file(parser, x))
    parser.add_argument("-facet", dest="facet", required=False,
                        help="the list of facets to apply to diagnosis",
                        type=str)
    parser.add_argument("-reactivate", dest="reactivate", required=False,
                        help="compute minimal correction sets wrt to the input facet",
                        type=str
                        )
    parser.add_argument("-impact", dest="impact", required=False,
                        help="compute consequences of removing a facet",
                        type=str
                        )
    parser.add_argument("-del", dest="delete", required=False,
                        help="delete the specified facets",
                        type=str
                        )
    parser.add_argument("-delall", dest="deleteAll", action="store_true", required=False,
                        help="indicate to delete all applied facets"
                        )

    args = parser.parse_args()
    if args.facet is not None:
        updateJustificationFile(args.filePath, args.facet)   
    if args.reactivate is not None:
        reactivateComputation.reactivate_function(args.filePath, args.reactivate)
    if args.impact is not None:
        impactComputation.impact_function(args.filePath, args.impact)
    if args.delete is not None:
        deletion.del_function(args.filePath, args.delete)
    if args.deleteAll:
        deletion.del_all(args.filePath)

def is_valid_file(parser, arg):
    if not os.path.exists(arg):
        parser.error("The file %s does not exist!" % arg)
    else:
        return arg

def updateJustificationFile(justificationsFilePath, facets): 

    facets_list = facets.split("/")
    facets_list = helperFunctions.handle_input_negation(facets_list)

    list_of_added_knowledge = []
    applicable_options = []
    try:
        with open("added_knowledge.txt", "r") as f:
            lines = f.readlines()
            for line in lines:
                list_of_added_knowledge.append(line.strip())
    except FileNotFoundError as e:
        list_of_added_knowledge = []

    
    minimalDiagnoses.fetch_globals()
    if not list_of_added_knowledge:
        # if no facet has been applied yet, use the initial allowed_entries as list of applicable facets else use the list_of_difference_blue
        applicable_options = minimalDiagnoses.allowed_entries
    else:
        applicable_options = diagnosis.converter(minimalDiagnoses.list_of_difference_blue)
        applicable_options = helperFunctions.add_point(applicable_options)
    facets_list = [e for e in facets_list if helperFunctions.transform_alpha_to_remove(e+'.') in applicable_options]

    asp_file = open(justificationsFilePath, "a")
    log_file = open("added_knowledge.txt", "a")

    for i in facets_list:
        facetId = i.split("alpha")[1]
        
        if "not" in i:
            asp_file.write(f":- not remove({facetId}). \n")
            log_file.write(f"remove({facetId}).\n")
            log_file.write(f"not alpha{facetId}().\n")
        else:
            asp_file.write(f":- remove({facetId}).\n")
            log_file.write(f"not remove({facetId}).\n")
            log_file.write(f"alpha{facetId}().\n")
    asp_file.close()
    log_file.close()


if __name__ == '__main__':
    main()