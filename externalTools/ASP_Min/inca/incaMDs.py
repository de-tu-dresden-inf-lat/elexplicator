from diagnosis import minimalDiagnoses
import os
import datetime
from argparse import ArgumentParser


def main():
    """
    
    :return: 
    """

    parser = ArgumentParser()

    parser.add_argument("-f", dest="filePath", required=True,
                        help="input file that contains the logic program", metavar="FILE",
                        type=lambda x: is_valid_file(parser, x))
    parser.add_argument("-m", dest="maxInt", required=True,
                        help="the maximum number of axioms identifiers",
                        type=lambda x: is_number(parser, x))
    parser.add_argument("-out", dest="mDsFilePath", required=True,
                        help="the path of the mDs file",
                        type=str)

    args = parser.parse_args()

    start = datetime.datetime.now()
    print(args.mDsFilePath)
    minimalDiagnoses.get_all_minimal_diagnoses(args.maxInt, args.filePath, args.mDsFilePath)

    print("done in " + str(datetime.datetime.now() - start))


def is_valid_file(parser, arg):
    if not os.path.exists(arg):
        parser.error("The file %s does not exist!" % arg)
    else:
        return arg


def is_number(parser,arg):
    try:
        val = int(arg)
        return val
    except ValueError:
        parser.error("%s is not an integer" % arg)


if __name__ == '__main__':
    main()

