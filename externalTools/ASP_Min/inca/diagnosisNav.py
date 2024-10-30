from argparse import ArgumentParser
import os

def main():
    parser = ArgumentParser()

    parser.add_argument("-path", dest="filePath", required=True,
                        help="input file that contains the logic program", metavar="FILE",
                        type=lambda x: is_valid_file(parser, x))
    parser.add_argument("-facet", dest="facet", required=True,
                        help="the facet to apply to diagnosis",
                        type=str)

    args = parser.parse_args()
    updateJustificationFile(args.filePath, args.facet)    

def is_valid_file(parser, arg):
    if not os.path.exists(arg):
        parser.error("The file %s does not exist!" % arg)
    else:
        return arg

def updateJustificationFile(justificationsFilePath, facet):
    facet = facet.split("alpha")[1]
    asp_file = open(justificationsFilePath, "a")
    asp_file.write(f":- remove({facet}). \n")
    asp_file.close()


if __name__ == '__main__':
    main()