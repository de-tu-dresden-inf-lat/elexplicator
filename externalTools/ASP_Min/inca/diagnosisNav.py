from argparse import ArgumentParser
import os
from diagnosis import minimalDiagnoses

def main():
    parser = ArgumentParser()

    parser.add_argument("-path", dest="filePath", required=True,
                        help="input file that contains the logic program", metavar="FILE",
                        type=lambda x: is_valid_file(parser, x))
    parser.add_argument("-facet", dest="facet", required=False,
                        help="the facet to apply to diagnosis",
                        type=str)
    parser.add_argument("-reactivate", dest="reactivate", action="store_true", required=False,
                        help="compute minimal correction sets wrt to the input facet"
                        )
    parser.add_argument("-impact", dest="impact", action="store_true", required=False,
                        help="compute consequences of removing a facet"
                        )

    args = parser.parse_args()
    if args.facet:
        updateJustificationFile(args.filePath, args.facet)   
    if args.reactivate:
        minimalDiagnosis.reactivate_function(args.filePath, args.reactivate)
    if args.impact:
        minimalDiagnosis.impact_function(args.filePath, args.impact_function)

def is_valid_file(parser, arg):
    if not os.path.exists(arg):
        parser.error("The file %s does not exist!" % arg)
    else:
        return arg

def updateJustificationFile(justificationsFilePath, facet):   
    facetId = facet.split("alpha")[1]
    asp_file = open(justificationsFilePath, "a")
    log_file = open("added_knowledge.txt", "a")
    if "not" in facet:
        asp_file.write(f":- not remove({facetId}). \n")
        log_file.write(f"remove({facetId})\n")
    else:
        asp_file.write(f":- remove({facetId}). \n")
        log_file.write(f"not remove({facetId})\n")
    asp_file.close()
    


if __name__ == '__main__':
    main()