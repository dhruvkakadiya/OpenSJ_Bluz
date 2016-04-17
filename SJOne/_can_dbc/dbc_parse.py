#!/usr/bin/python

import sys, getopt
import re

"""
@Author: Preet
This parses the Vector DBC file to generate code to marshal and unmarshal DBC defined messages

Use Python (I used Python 3.5)
python dbc_parse.py -i 243.dbc -s MOTOR
Generate all code: dbc_parse.py -i 243.dbc -s MOTOR -a all > generated.h
"""

LINE_BEG = '%'


def is_empty(s):
    if s:
        return False
    else:
        return True


class Signal(object):
    def __init__(self, name, bit_start, bit_size, is_unsigned, scale, offset, min_val, max_val, recipients, mux):
        self.name = name
        self.bit_start = int(bit_start)
        self.bit_size = int(bit_size)
        self.is_unsigned = is_unsigned

        self.offset = float(offset)
        self.offset_str = offset
        self.scale = float(scale)
        self.scale_str = scale
        self.min_val = float(min_val)
        self.min_val_str = min_val
        self.max_val = float(max_val)
        self.max_val_str = max_val

        self.recipients = recipients
        self.enum_info = {}
        self.mux = mux
        if self.mux == '':
            self.mux = '__NO__MUX__'

    def is_enum_type(self):
        return not is_empty(self.enum_info)

    def is_muxed(self):
        return '__NO__MUX__' != self.mux

    def get_code_var_type(self):
        if '.' in self.scale_str:
            return "float"
        else:
            if not is_empty(self.enum_info):
                return self.name + "_E"

            _max = (2 ** self.bit_size) * self.scale
            if not self.is_unsigned:
                _max *= 2

            t = "uint32_t"
            if _max <= 256:
                t = "uint8_t"
            elif _max <= 65536:
                t = "uint16_t"

            if not self.is_unsigned:
                t = t[1:]

            return t

    def get_signal_code(self):
        code = ""
        code += "    " + self.get_code_var_type() + " " + self.name
        if self.bit_size <= 4:
             code += " : " + str(self.bit_size) + ";"
        else:
             code += ";"

        # Align the start of the comments
        for i in range(len(code), 45):
            code += " "

        # Comment with Min/Max
        code += " ///< B" + str(self.bit_start + self.bit_size - 1) + ":" + str(self.bit_start)
        if self.min_val != 0 or self.max_val != 0:
            code += "  Min: " + self.min_val_str + " Max: " + self.max_val_str

        # Comment with destination nodes:
        code += "   Destination: "
        for r in self.recipients:
            if r == self.recipients[0]:
                code += r
            else:
                code += "," + r

        return code + "\n"

    def get_encode_code(self, raw_sig_name, var_name):
        code = ''

        # Min/Max check
        if self.min_val != 0 or self.max_val != 0:
            code += ("    if(" + var_name + " < " + self.min_val_str + ") { " + var_name + " = " + self.min_val_str + "; }\n")
            code += ("    if(" + var_name + " > " + self.max_val_str + ") { " + var_name + " = " + self.max_val_str + "; }\n")

        # Compute binary value (both little and big endian)
        # Encode should subtract offset then divide
        code += ("    " + raw_sig_name + " = ((uint64_t)(((" + var_name + " - (" + self.offset_str + ")) / " + str(self.scale) + ") + 0.5))")
        code += (" & 0x" + format(2 ** self.bit_size - 1, '02x') + ";\n")

        bit_pos = self.bit_start
        remaining = self.bit_size
        byte_num = int(self.bit_start / 8)
        while remaining > 0:
            if remaining > 8:
                bits_in_this_byte = 8 - (bit_pos % 8)
            else:
                bits_in_this_byte = remaining

            code += ("    bytes[" + str(byte_num) + "] |= (((uint8_t)(" + raw_sig_name + " >> " + str(bit_pos - self.bit_start) + ")")
            code += (" & 0x" + format(2 ** bits_in_this_byte - 1, '02x') + ") << " + str(bit_pos % 8) + ")")
            code += ("; ///< " + str(bits_in_this_byte) + " bit(s) starting from B" + str(bit_pos) + "\n")
            byte_num += 1

            bit_pos += bits_in_this_byte
            remaining -= bits_in_this_byte
        return code

    def get_decode_code(self, raw_sig_name, prefix=''):
        # Little and Big Endian:
        bit_pos = self.bit_start
        remaining = self.bit_size
        byte_num = int(self.bit_start / 8)
        bit_count = 0
        code = ''

        while remaining > 0:
            if remaining > 8:
                bits_in_this_byte = 8 - (bit_pos % 8)
            else:
                bits_in_this_byte = remaining

            code += (LINE_BEG + raw_sig_name + " |= ((uint64_t)((bytes[" + str(byte_num) + "] >> " + str(bit_pos % 8) + ")")
            code += (" & 0x" + format(2 ** bits_in_this_byte - 1, '02x') + ")) << " + str(bit_count) + ";")
            code += (" ///< " + str(bits_in_this_byte) + " bit(s) from B" + str(bit_pos) + "\n")

            if bit_count == 0:
                code = code.replace("|=", " =")

            byte_num += 1
            bit_pos += bits_in_this_byte
            remaining -= bits_in_this_byte
            bit_count += bits_in_this_byte

        # Decode/get should multiply then add the offset
        enum_cast = ''
        if self.is_enum_type():
            enum_cast = "(" + self.get_code_var_type() + ")"

        code += (prefix + self.name + " = " + enum_cast + " ((" + raw_sig_name + " * " + str(self.scale) + ") + (" + self.offset_str + "));\n")

        return code


class Message(object):
    """
    Message Object that contains the list of signals inside
    """

    def __init__(self, mid, name, dlc, sender):
        self.mid = mid
        self.name = name
        self.dlc = dlc
        self.sender = sender
        self.signals = []

    def add_signal(self, s):
        self.signals.append(s)

    def get_struct_name(self):
        return "%s_TX_%s_t" % (self.sender, self.name)

    def is_recipient_of_at_least_one_sig(self, node):
        for s in self.signals:
            if node in s.recipients:
                return True
        return False

    def contains_muxed_signals(self):
        for s in self.signals:
            if s.is_muxed():
                return True
        return False

    def contains_enums(self):
        for s in self.signals:
            if not is_empty(s.enum_info):
                return True
        return False

    def get_muxes(self):
        muxes = []
        for s in self.signals:
            if s.is_muxed() and s.mux not in muxes:
                muxes.append(s.mux)
        return muxes

    def get_mux_index_signal(self):
        for s in self.signals:
            if s.is_muxed() and s.mux == "M":
                return s
        return ""

    # TODO: Do not generate this struct if we are not the recipient of any of the signals of this MUX
    def get_struct_for_mux(self, mux, non_muxed_signals):
        code = '\n'
        code += ("/// Struct for MUX: " + mux + "\n")
        code += ("typedef struct {\n")
        code += non_muxed_signals

        for s in self.signals:
            if s.mux == mux:
                code += (s.get_signal_code())
        code += ("\n    mia_info_t mia_info;")
        code += ("\n} " + self.get_struct_name()[:-2] + "_" + str(mux) + "_t;\n")
        return code

    def gen_converted_struct(self, self_node, gen_all):
        code = ''
        if self.contains_muxed_signals():
            # Non Muxed signals in this struct, exclude the MUXED index
            non_muxed_signals = ''
            for s in self.signals:
                if not s.is_muxed() and not s.mux == "M":
                    non_muxed_signals += (s.get_signal_code())

            # MUX'd data structures
            code = ("/// @{ MUX'd message: " + self.name + "\n")
            muxes = self.get_muxes()
            for m in muxes[1:]:
                code += self.get_struct_for_mux(m, non_muxed_signals)

            # Parent data structure
            code += ("\n/// Struct with all the child MUX'd signals\n")
            code += ("typedef struct {\n")

            # Child struct instances of the Mux'd signals
            for m in muxes[1:]:
                code += ("    " + self.get_struct_name()[:-2] + "_" + str(m) + "_t " + str(m) + "; ///< MUX'd structure\n")
            code += ("} " + self.get_struct_name() + ";\n")

            code += ("/// @} MUX'd message\n")
        else:
            code += ("\n/// Message: " + self.name + " from '" + self.sender + "', DLC: " + self.dlc + " byte(s), MID: " + self.mid + "\n")
            code += ("typedef struct {\n")
            for s in self.signals:
                if gen_all or self_node in s.recipients or self.sender == self_node:
                    code += (s.get_signal_code())

            code += ("\n    mia_info_t mia_info;")
            code += ("\n} " + self.get_struct_name() + ";\n")

        return code

    def get_encode_code(self):
        code = ''
        if self.contains_muxed_signals():
            muxes = self.get_muxes()
            for mux in muxes[1:]:
                code += ("static msg_hdr_t " + self.get_struct_name()[:-2] + "_" + str(mux) + "_encode")
                code += ("(uint64_t *to, " + self.get_struct_name()[:-2] + "_" + str(mux) + "_t *from)\n")
                code += ("{\n")
                code += ("    uint64_t raw;\n")
                code += ("    uint8_t *bytes = (uint8_t*) to;\n")
                code += ("    *to = 0; ///< Default the entire destination data with zeroes\n\n")
                code += ("    // Set the MUX index value\n")
                muxed_idx = self.get_mux_index_signal()
                code += muxed_idx.get_encode_code("raw", str(mux)[1:])
                code += ("\n")

                # Non Muxed signals in this struct, exclude the MUXED index
                code += "    // Set non MUX'd signals that need to go out with this MUX'd message\n"
                for s in self.signals:
                    if not s.is_muxed() and not s.mux == "M":
                        code += s.get_encode_code("raw", "from->" + s.name)

                # Rest of the signals that are part of this MUX
                code += ("\n")
                code += ("    // Set the rest of the signals within this MUX (" + mux + ")\n")
                for s in self.signals:
                    if mux == s.mux:
                        code += s.get_encode_code("raw", "from->" + s.name)

                code += ("\n")
                code += ("    return " + self.get_struct_name()[:-2] + "_HDR;\n")
                code += ("}\n")
        else:
            code += ("\n/// Encode " + self.sender + "'s '" + self.name + "' message\n")
            code += ("/// @returns the message header of this message\n")
            code += ("static msg_hdr_t " + self.get_struct_name()[:-2] + "_encode(uint64_t *to, " + self.get_struct_name() + " *from)\n")
            code += ("{\n")
            code += ("    *to = 0; ///< Default the entire destination data with zeroes\n")
            code += ("    uint64_t raw;\n")
            code += ("    uint8_t *bytes = (uint8_t*) to;\n")
            code += ("\n")

            for s in self.signals:
                code += s.get_encode_code("raw", "from->" + s.name) + "\n"

            code += ("    return " + self.get_struct_name()[:-2] + "_HDR;\n")
            code += ("}\n")

        return code

    def get_non_mux_signal_decode_code(self, raw_sig_name, prefix=''):
        code = ''
        for s in self.signals:
            if not s.is_muxed():
                code += s.get_decode_code(raw_sig_name, prefix)
        return code

    def get_signal_decode_code_for_mux(self, mux, raw_sig_name, prefix=''):
        code = ''
        for s in self.signals:
            if s.mux == mux:
                code += s.get_decode_code(raw_sig_name, prefix)
        return code

    def get_decode_code(self):
        raw_sig_name = "raw"
        code = ''
        code += ("\n/// Decode " + self.sender + "'s '" + self.name + "' message\n")
        code += ("/// @param hdr  The header of the message to validate its DLC and MID; this can be NULL to skip this check\n")
        code += ("static inline bool " + self.get_struct_name()[:-2] + "_decode(" + self.get_struct_name() + " *to, const uint64_t *from, const msg_hdr_t *hdr)\n")
        code += ("{\n")
        code += ("    const bool success = true;\n")
        code += ("    if (NULL != hdr && (hdr->dlc != " + self.get_struct_name()[:-2] + "_HDR.dlc || hdr->mid != " + self.get_struct_name()[:-2] + "_HDR.mid)) {\n")
        code += ("        return !success;\n")
        code += ("    }\n")
        code += ("    uint64_t " + raw_sig_name + ";\n")
        code += ("    const uint8_t *bytes = (const uint8_t*) from;\n\n")

        if self.contains_muxed_signals():
            # Decode the Mux and store it into it own variable type
            muxed_sig = self.get_mux_index_signal()
            code += ("    // Decode the MUX\n")
            code += (muxed_sig.get_decode_code(raw_sig_name).replace(LINE_BEG, "    ")).replace(muxed_sig.name, "    const " + muxed_sig.get_code_var_type() + " MUX")
            code += ("\n")

            # Decode the Mux'd signal(s)
            muxes = self.get_muxes()
            for mux in muxes[1:]:
                prefix = "%to->" + mux + "."

                # Each MUX'd message may also have non muxed signals:
                non_mux_code = self.get_non_mux_signal_decode_code(raw_sig_name, prefix)
                mux_code = self.get_signal_decode_code_for_mux(mux, raw_sig_name, prefix)

                if mux == muxes[1]:
                    code += ("    if (" + str(mux)[1:] + " == MUX) {\n")
                else:
                    code += ("    else if (" + str(mux)[1:] + " == MUX) {\n")

                if non_mux_code != '':
                    code += "        // Non Muxed signals (part of all MUX'd structures)\n"
                    code += non_mux_code.replace(LINE_BEG, "        ")
                    code += "\n"
                code += mux_code.replace(LINE_BEG, "        ")

                code += ("\n        to->" + str(mux) + ".mia_info.mia_counter_ms = 0; ///< Reset the MIA counter\n")
                code += ("    }\n")
            code += "    else {\n        return !success;\n    }\n"
        else:
            code += self.get_non_mux_signal_decode_code(raw_sig_name, "    to->").replace(LINE_BEG, "    ")
            code += ("\n")
            code += ("    to->mia_info.mia_counter_ms = 0; ///< Reset the MIA counter\n")

        code += ("\n    return success;\n")
        code += ("}\n")
        return code


class DBC(object):
    def __init__(self, name, self_node, gen_all):
        self.name = name
        self.self_node = self_node
        self.gen_all = gen_all
        self.messages = []
        self.nodes = []

    def gen_file_header(self):
        code = ''
        code += ("/// DBC file: %s    Self node: '%s'\n" % (self.name, self.self_node))
        code += ("/// This file can be included by a source file, for example: #include \"generated.h\"\n")
        code += ("#ifndef __GENEARTED_DBC_PARSER\n")
        code += ("#define __GENERATED_DBC_PARSER\n")
        code += ("#include <stdbool.h>\n")
        code += ("#include <stdint.h>\n")
        code += ("#include <stdlib.h>\n")
        return code

    def gen_msg_hdr_struct(self):
        code = ("/// CAN message header structure\n")
        code += ("typedef struct { \n")
        code += ("    uint32_t mid; ///< Message ID of the message\n")
        code += ("    uint8_t  dlc; ///< Data length of the message\n")
        code += ("} msg_hdr_t; \n")
        return code

    def gen_enum_types(self):
        code = ''
        for m in self.messages:
            if not m.contains_enums():
                continue
            if m.is_recipient_of_at_least_one_sig(self.self_node) or self.self_node == m.sender:
                code += ("/// Enumeration for Message: '" + m.name + "' from '" + m.sender + "'\n")
                for s in m.signals:
                    code += "typedef enum {\n"
                    for key in s.enum_info:
                        code += "    " + key + " = " + s.enum_info[key] + ",\n"
                    code += "} " + s.name + "_E ;\n"
        code += "\n"
        return code

    def gen_msg_hdr_instances(self):
        code = ''
        for m in self.messages:
            if not self.gen_all and not m.is_recipient_of_at_least_one_sig(self.self_node) and self.self_node != m.sender:
                code += "// "
            code += ("static const msg_hdr_t " + (m.get_struct_name()[:-2] + "_HDR = ").ljust(32 + 7))
            code += ("{ " +  str(m.mid).rjust(4) + ", " + m.dlc + " };\n")
        return code

    def gen_mia_struct(self):
        code = ("/// Missing in Action structure\n")
        code += ("typedef struct {\n")
        code += ("    uint32_t is_mia : 1;          ///< Missing in action flag\n")
        code += ("    uint32_t mia_counter_ms : 31; ///< Missing in action counter\n")
        code += ("} mia_info_t;\n")
        return code

    def _gen_mia_func_header(self, sender, msg_name):
        code = ''
        code += ("\n/// Handle the MIA for " + sender + "'s " + msg_name + " message\n")
        code += ("/// @param   time_incr_ms  The time to increment the MIA counter with\n")
        code += ("/// @returns true if the MIA just occurred\n")
        code += ("/// @post    If the MIA counter is not reset, and goes beyond the MIA value, the MIA flag is set\n")
        return code

    def _get_mia_func_body(self, msg_name):
        code = ''
        code += ("{\n")
        code += ("    bool mia_occurred = false;\n")
        code += ("    const mia_info_t old_mia = msg->mia_info;\n")
        code += ("    msg->mia_info.is_mia = (msg->mia_info.mia_counter_ms >= " + msg_name + "__MIA_MS);\n")
        code += ("\n")
        code += ("    if (!msg->mia_info.is_mia) { \n")
        code += ("        msg->mia_info.mia_counter_ms += time_incr_ms;\n")
        code += ("    }\n")
        code += ("    else if(!old_mia.is_mia)   { \n")
        code += ("        // Copy MIA struct, then re-write the MIA counter and is_mia that is overwriten\n")
        code += ("        *msg = " + msg_name + "__MIA_MSG;\n")
        code += ("        msg->mia_info.mia_counter_ms = " + msg_name + "__MIA_MS;\n")
        code += ("        msg->mia_info.is_mia = true;\n")
        code += ("        mia_occurred = true;\n")
        code += ("    }\n")
        code += ("\n    return mia_occurred;\n")
        code += ("}\n")
        return code

    def gen_mia_funcs(self):
        code = ''

        # Generate MIA handler for the dbc.messages we are a recipient of
        for m in self.messages:
            if not self.gen_all and not m.is_recipient_of_at_least_one_sig(self.self_node):
                continue
            if m.contains_muxed_signals():
                muxes = m.get_muxes()
                for mux in muxes[1:]:
                    code += self._gen_mia_func_header(m.sender, m.name + " for MUX \"" + mux + '"')
                    code += ("static inline bool " + m.get_struct_name()[:-2] + "_" + mux + "_handle_mia(")
                    code += (m.get_struct_name()[:-2] + "_" + mux + "_t *msg, uint32_t time_incr_ms)\n")
                    code += self._get_mia_func_body(m.name + "_" + mux)
            else:
                code += self._gen_mia_func_header(m.sender, m.name)
                code += ("static inline bool " + m.get_struct_name()[:-2] + "_handle_mia(" + m.get_struct_name() + " *msg, uint32_t time_incr_ms)\n")
                code += self._get_mia_func_body(m.name)
            
        return code


def main(argv):
    dbcfile = '243.dbc'     # Default value unless overriden
    self_node = 'DRIVER'    # Default value unless overriden
    gen_all = False
    big_endian = False

    try:
        opts, args = getopt.getopt(argv, "hi:s:a:b", ["ifile=", "self=", "all", "big"])
    except getopt.GetoptError:
        print ('dbc_parse.py -i <dbcfile> -s <self_node> <-a> <-b>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print ('dbc_parse.py -i <dbcfile> -s <self_node> <-a> <-b>')
            sys.exit()
        elif opt in ("-i", "--ifile"):
            dbcfile = arg
        elif opt in ("-s", "--self"):
            self_node = arg
        elif opt in ("-a", "--all"):
            gen_all = True
        elif opt in ("-b", "--big"):
            big_endian = True

    # Parse the DBC file
    dbc = DBC(dbcfile, self_node, gen_all)
    f = open(dbcfile, "r")
    while 1:
        line = f.readline()
        if not line:
            break

        # Nodes in the DBC file
        if line.startswith("BU_:"):
            nodes = line.strip("\n").split(' ')
            dbc.nodes = (nodes[1:])
            if self_node not in dbc.nodes:
                print ('/////////////////////////////// ERROR /////////////////////////////////////')
                print ('#error "Self node: ' + self_node + ' not found in _BU nodes in the DBC file"')
                print ('/////////////////////////////// ERROR /////////////////////////////////////')
                print ('')

        # Start of a message
        if line.startswith("BO_ "):
            tokens = line.split(' ')
            msg = Message(tokens[1], tokens[2].strip(":"), tokens[3], tokens[4].strip("\n"))
            dbc.messages.append(msg)

        # Signals
        if line.startswith(" SG_ "):
            t = line[1:].split(' ')

            # If this is a MUX'd symbol
            mux = ''
            if t[3] == ":":
                mux = t[2]
                line = line.replace(mux + " ", '')
                t = line[1:].split(' ')

            # Split the bit start and the bit size
            s = re.split('[|@]', t[3])
            bit_start = s[0]
            bit_size = s[1]
            is_unsigned = '+' in s[2]

            # Split (0.1,1) to two tokens by removing the ( and the )
            s = t[4][1:-1].split(',')
            scale = s[0]
            offset = s[1]

            # Split the [0|0] to min and max
            s = t[5][1:-1].split('|')
            min_val = s[0]
            max_val = s[1]

            recipients = t[7].strip('\n').split(',')

            # Add the signal the last message object
            sig = Signal(t[1], bit_start, bit_size, is_unsigned, scale, offset, min_val, max_val, recipients, mux)
            dbc.messages[-1].add_signal(sig)

        # Enumeration types
        if line.startswith("VAL_ "):
            t = line[1:].split(' ')
            sig_mid = t[1]
            enum_name = t[2]
            pairs = {}
            t = t[3:]
            for i in range(0, int(len(t)/2)):
                pairs[t[i*2+1].replace('"', '').replace(';\n', '')] = t[i*2]

            # Locate the message and the signal whom this enumeration type belongs to
            for msg in dbc.messages:
                if msg.mid == sig_mid:
                    for s in msg.signals:
                        if s.name == enum_name:
                            s.enum_info = pairs
                            break

    print (dbc.gen_file_header())
    print ("\n")

    # Generate header structs and MIA struct
    print (dbc.gen_mia_struct())
    print (dbc.gen_msg_hdr_struct())
    print (dbc.gen_msg_hdr_instances())
    print (dbc.gen_enum_types())

    # Generate converted struct types for each message
    for m in dbc.messages:
        if not gen_all and not m.is_recipient_of_at_least_one_sig(self_node) and m.sender != self_node:
            code = ("\n/// Not generating '" + m.get_struct_name() + "' since we are not the sender or a recipient of any of its signals")
        else:
            print (m.gen_converted_struct(self_node, gen_all))

    # Generate MIA handler "externs"
    print ("\n/// These 'externs' need to be defined in a source file of your project")
    for m in dbc.messages:
        if gen_all or m.is_recipient_of_at_least_one_sig(self_node):
            if m.contains_muxed_signals():
                muxes = m.get_muxes()
                for mux in muxes[1:]:
                    print (str("extern const uint32_t ").ljust(50) + (m.name + "_" + mux + "__MIA_MS;"))
                    print (str("extern const " + m.get_struct_name()[:-2] + "_" + mux + "_t").ljust(49) + " " + (m.name + "_" + mux + "__MIA_MSG;"))
            else:
                print (str("extern const uint32_t ").ljust(50) + (m.name + "__MIA_MS;"))
                print (str("extern const " + m.get_struct_name()).ljust(49) + " " + (m.name + "__MIA_MSG;"))

    # Generate encode methods
    for m in dbc.messages:
        if not gen_all and m.sender != self_node:
            print ("\n/// Not generating code for " + m.get_struct_name()[:-2] + "_encode() since the sender is " + m.sender + " and we are " + self_node)
        else:
            print (m.get_encode_code())

    # Generate decode methods
    for m in dbc.messages:
        if not gen_all and not m.is_recipient_of_at_least_one_sig(self_node):
            print ("\n/// Not generating code for " + m.get_struct_name()[:-2] + "_decode() since '" + self_node + "' is not the recipient of any of the signals")
        else:
            print (m.get_decode_code())

    print (dbc.gen_mia_funcs())
    print ("#endif")


if __name__ == "__main__":
    main(sys.argv[1:])
