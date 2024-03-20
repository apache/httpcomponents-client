/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.client5.http.examples;


import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * This class contains unit tests to verify the implementation of the Happy Eyeballs V2 connection
 * operator, which applies a set of rules to determine the optimal order in which to connect to
 * multiple IP addresses for a given hostname. These tests verify that the implementation follows
 * the specified rules correctly and returns the expected results.
 */
public class HappyEyeballsV2RulesTest {


    public static void main(final String[] args) throws Exception {

        System.out.println("---- Running HappyEyeballsV2RulesTest ----");

        rule1();
        rule2();
        rule3();
        rule4();
        rule5();
        rule6();
        rule7();
        rule8();
        rule9();


        System.out.println("---- All tests passed. ----");
    }

    public static void rule1() throws Exception {
        final InetAddress addr1 = InetAddress.getByName("192.0.2.1");
        final InetAddress addr2 = InetAddress.getByName("2001:db8::1");
        final InetAddress addr3 = InetAddress.getByName("www.google.com");

        final List<InetAddress> addresses = Arrays.asList(addr3, addr2, addr1);
        addresses.sort((addr11, addr22) -> {
            // Get the list of network interfaces
            final List<NetworkInterface> networkInterfaces;
            try {
                networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            } catch (final SocketException e) {
                return 0;
            }

            // Rule 1: Avoid unusable destinations.
            final boolean add1IsReachable;
            final boolean add2IsReachable;
            try {
                // Check if address 1 is reachable on any network interface
                add1IsReachable = isAddressReachable(addr1, networkInterfaces);
            } catch (final IOException e) {
                return -1;
            }
            try {
                // Check if address 2 is reachable on any network interface
                add2IsReachable = isAddressReachable(addr2, networkInterfaces);
            } catch (final IOException e) {
                return 1;
            }

            if (add1IsReachable && !add2IsReachable) {
                return -1;
            } else if (!add1IsReachable && add2IsReachable) {
                return 1;
            }

            return 0;

        });

        if (!addresses.get(0).equals(addr3)) {
            throw new Exception("Rule 1 not satisfied. Expected addr3 as the first address.");
        }

        if (!addresses.get(1).equals(addr2)) {
            throw new Exception("Rule 1 not satisfied. Expected addr2 as the second address.");
        }

        if (!addresses.get(2).equals(addr1)) {
            throw new Exception("Rule 1 not satisfied. Expected addr1 as the third address.");
        }

        System.out.println("Rule 1 satisfied.");
    }


    public static void rule2() throws Exception {
        final InetAddress addr1 = InetAddress.getByName("192.0.2.1");
        final InetAddress addr2 = InetAddress.getByName("2001:db8::1");
        final InetAddress addr3 = InetAddress.getByName("www.google.com");

        final InetAddress localhost;
        final InetAddress sourceAddr;
        try {
            localhost = InetAddress.getLocalHost();
            sourceAddr = InetAddress.getByName(localhost.getHostAddress());
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }

        final List<InetAddress> addresses = Arrays.asList(addr3, addr1, addr2);
        addresses.sort((addr11, addr21) -> {
            final int scope1 = getScope(addr11);
            final int scope2 = getScope(addr21);
            final int scopeSource = getScope(sourceAddr);

            if (scope1 == scope2) {
                return 0;
            } else if (scope1 == scopeSource) {
                return -1;
            } else if (scope2 == scopeSource) {
                return 1;
            } else {
                return 0;
            }
        });

        // Check if the result is correct
        if (!addresses.get(0).equals(addr3)) {
            throw new Exception("Rule 2 not satisfied. Expected www.google.com as the first address.");
        }

        if (!addresses.get(1).equals(addr1)) {
            throw new Exception("Rule 2 not satisfied. Expected addr1 as the second address.");
        }

        if (!addresses.get(2).equals(addr2)) {
            throw new Exception("Rule 2 not satisfied. Expected addr2 as the third address.");
        }
        System.out.println("Rule 2 satisfied.");
    }

    public static void rule3() throws Exception {
        final InetAddress srcAddr1 = InetAddress.getByName("192.0.2.1");
        final InetAddress dstAddr1 = InetAddress.getByName("www.google.com");
        final InetAddress srcAddr2 = InetAddress.getByName("2001:db8::1");
        final InetAddress dstAddr2 = InetAddress.getByName("2001:db8::2");

        final List<InetAddress> addresses = Arrays.asList(dstAddr2, dstAddr1, srcAddr2, srcAddr1);
        addresses.sort((addr1, addr2) -> {
            final boolean addr1IsDeprecated = isDeprecated(addr1);
            final boolean addr2IsDeprecated = isDeprecated(addr2);
            final boolean addr1IsSource = isSourceAddress(addr1, srcAddr1, srcAddr2);
            final boolean addr2IsSource = isSourceAddress(addr2, srcAddr1, srcAddr2);

            if (addr1IsDeprecated && !addr2IsDeprecated) {
                return 1;
            } else if (!addr1IsDeprecated && addr2IsDeprecated) {
                return -1;
            } else if (addr1IsSource && !addr2IsSource) {
                return -1;
            } else if (!addr1IsSource && addr2IsSource) {
                return 1;
            } else {
                return 0;
            }
        });

        if (!addresses.get(0).equals(srcAddr2)) {
            throw new Exception("Rule 3 not satisfied. Expected srcAddr2 as the first address.");
        }

        if (!addresses.get(1).equals(srcAddr1)) {
            throw new Exception("Rule 3 not satisfied. Expected srcAddr1 as the second address.");
        }

        if (!addresses.get(2).equals(dstAddr2)) {
            throw new Exception("Rule 3 not satisfied. Expected dstAddr2 as the third address.");
        }

        if (!addresses.get(3).equals(dstAddr1)) {
            throw new Exception("Rule 3 not satisfied. Expected srcAddr2 as the fourth address.");
        }

        System.out.println("Rule 3 satisfied.");
    }

    public static void rule4() throws Exception {
        final InetAddress addr1 = InetAddress.getByName("192.0.2.1");
        final InetAddress addr2 = InetAddress.getByName("192.168.0.1");
        final InetAddress addr3 = InetAddress.getByName("2001:db8::1");
        final InetAddress addr4 = InetAddress.getByName("2001:db8::2");

        final List<InetAddress> addresses = Arrays.asList(addr3, addr1, addr2, addr4);

        addresses.sort((addr11, addr21) -> {
            // Rule 4: Prefer home addresses.
            final boolean isHomeAddr11 = isHomeAddress(addr11);
            final boolean isCareOfAddr11 = isCareOfAddress(addr11);
            final boolean isHomeAddr21 = isHomeAddress(addr21);
            final boolean isCareOfAddr21 = isCareOfAddress(addr21);

            if (isHomeAddr11 && isCareOfAddr11 && !isHomeAddr21) {
                return -1;
            } else if (isHomeAddr21 && isCareOfAddr21 && !isHomeAddr11) {
                return 1;
            }
            return 0;
        });


        if (!addresses.get(0).equals(addr3)) {
            throw new Exception("Rule 4 not satisfied. Expected addr1 as the first address.");
        }

        System.out.println("Rule 4 satisfied.");
    }

    public static void rule5() throws Exception {
        final InetAddress addr1 = InetAddress.getByName("192.0.2.1");
        final InetAddress addr2 = InetAddress.getByName("2001:db8::1");
        final InetAddress addr3 = InetAddress.getByName("www.google.com");

        final List<InetAddress> addresses = Arrays.asList(addr3, addr1, addr2);

        addresses.sort((addr11, addr21) -> {
            //Rule 5: Prefer matching label.
            final int label1 = getLabel(addr11);
            final int label2 = getLabel(addr21);

            if (label1 < label2) {
                return -1;
            } else if (label1 > label2) {
                return 1;
            }

            return 0;
        });

        if (!addresses.get(0).equals(addr2)) {
            throw new Exception("Rule 5 not satisfied. Expected 2001:db8::1 as the first address.");
        }

        if (!addresses.get(1).equals(addr3)) {
            throw new Exception("Rule 5 not satisfied. Expected www.google.com as the second address.");
        }

        if (!addresses.get(2).equals(addr1)) {
            throw new Exception("Rule 5 not satisfied. Expected 192.0.2.1 as the third address.");
        }

        System.out.println("Rule 5 satisfied.");
    }

    public static void rule6() throws Exception {
        final InetAddress addr1 = InetAddress.getByName("2001:db8:0:0:1::");
        final InetAddress addr2 = InetAddress.getByName("2001:db8:0:0:2::");

        final List<InetAddress> addresses = Arrays.asList(addr2, addr1);

        addresses.sort((addr11, addr21) -> {
            // Rule 6: Prefer higher precedence.
            final int add1Precedence = getPrecedence(addr11);
            final int add2Precedence = getPrecedence(addr21);

            if (add1Precedence > add2Precedence) {
                return -1;
            } else if (add1Precedence < add2Precedence) {
                return 1;
            }

            return 0;
        });

        if (!addresses.get(0).equals(addr2)) {
            throw new Exception("Rule 6 not satisfied. Expected addr2 as the first address.");
        }

        System.out.println("Rule 6 satisfied.");
    }


    public static void rule7() throws Exception {
        final InetAddress addr1 = InetAddress.getByName("2001:db8:0:0:1::");
        final InetAddress addr2 = InetAddress.getByName("2001:db8:0:0:2::");

        final List<InetAddress> addresses = Arrays.asList(addr2, addr1);

        addresses.sort((addr11, addr21) -> {
            // Rule 7: Prefer native transport.
            final boolean addr1Encapsulated = isEncapsulated(addr11);
            final boolean addr2Encapsulated = isEncapsulated(addr21);

            if (addr1Encapsulated && !addr2Encapsulated) {
                return 1;
            } else if (!addr1Encapsulated && addr2Encapsulated) {
                return -1;
            }

            return 0;
        });

        if (!addresses.get(0).equals(addr2)) {
            throw new Exception("Rule 7 not satisfied. Expected 2001:db8:0:0:2:: as the first address.");
        }

        if (!addresses.get(1).equals(addr1)) {
            throw new Exception("Rule 7 not satisfied. Expected 2001:db8:0:0:1:: as the second address.");
        }
        System.out.println("Rule 7 satisfied.");
    }

    public static void rule8() throws Exception {
        final InetAddress addr1 = InetAddress.getByName("2001:db8:0:0:0:0:0:1");
        final InetAddress addr2 = InetAddress.getByName("2001:db8:0:1:0:0:0:1");
        final InetAddress addr3 = InetAddress.getByName("2001:db8:0:2:0:0:0:1");
        final InetAddress addr4 = InetAddress.getByName("::1");
        final InetAddress addr5 = InetAddress.getByName("fe80::1%lo0");

        final List<InetAddress> addresses = Arrays.asList(addr4, addr5, addr3, addr1, addr2);

        addresses.sort((addr11, addr21) -> {
            // Rule 8: Prefer smaller scope.
            final int scope1 = getScope(addr11);
            final int scope2 = getScope(addr21);

            if (scope1 < scope2) {
                return -1;
            } else if (scope1 > scope2) {
                return 1;
            } else {
                return 0;
            }
        });

        if (!addresses.get(0).equals(addr4)) {
            throw new Exception("Rule 8 not satisfied. Expected fe80::1%lo0 as the first address.");
        }

        if (!addresses.get(1).equals(addr5)) {
            throw new Exception("Rule 8 not satisfied. Expected ::1 as the second address.");
        }

        if (!addresses.get(2).equals(addr3)) {
            throw new Exception("Rule 8 not satisfied. Expected 2001:db8:0:0:0:0:0:1 as the third address.");
        }

        if (!addresses.get(3).equals(addr1)) {
            throw new Exception("Rule 8 not satisfied. Expected 2001:db8:0:1:0:0:0:1 as the fourth address.");
        }

        if (!addresses.get(4).equals(addr2)) {
            throw new Exception("Rule 8 not satisfied. Expected 2001:db8:0:2:0:0:0:1 as the fifth address.");
        }

        System.out.println("Rule 8 satisfied.");
    }

    public static void rule9() throws Exception {
        final InetAddress addr1 = InetAddress.getByName("2001:db8:0:0:1::");
        final InetAddress addr2 = InetAddress.getByName("2001:db8:0:0:1:0:0:2");
        final InetAddress addr3 = InetAddress.getByName("2001:db8:0:0:1:0:0:3");
        final InetAddress addr4 = InetAddress.getByName("2001:db8:0:0:2:0:0:4");

        final List<InetAddress> addresses = Arrays.asList(addr4, addr1, addr2, addr3);

        addresses.sort((addr11, addr21) -> {
            // Rule 9: Use longest matching prefix.
            final int prefixLen1 = commonPrefixLen(addr11, addr21);
            final int prefixLen2 = commonPrefixLen(addr21, addr11);

            if (prefixLen1 > prefixLen2) {
                return -1;
            } else if (prefixLen1 < prefixLen2) {
                return 1;
            }
            return 0;
        });

        if (!addresses.get(0).equals(addr4)) {
            throw new Exception("Rule 9 not satisfied. Expected addr1 as the first address.");
        }

        System.out.println("Rule 9 satisfied.");
    }


    private static int getPrecedence(final InetAddress addr) {
        if (addr instanceof Inet6Address) {
            final byte[] addrBytes = addr.getAddress();
            if (addrBytes[0] == (byte) 0xFF) {
                return 1; // multicast
            } else if (isIPv4MappedIPv6Address(addrBytes)) {
                return 4; // IPv4-mapped IPv6 address
            } else if (isULA(addrBytes)) {
                return 5; // unique local address
            } else if (isLinkLocal(addrBytes)) {
                return 6; // link-local address
            } else if (isSiteLocal(addrBytes)) {
                return 7; // site-local address
            } else {
                return 3; // global address
            }
        } else {
            return 2; // IPv4 address
        }
    }

    private static boolean isIPv4MappedIPv6Address(final byte[] addr) {
        return addr.length == 16 && addr[0] == 0x00 && addr[1] == 0x00 && addr[2] == 0x00
                && addr[3] == 0x00 && addr[4] == 0x00 && addr[5] == 0x00 && addr[6] == 0x00
                && addr[7] == 0x00 && addr[8] == 0x00 && addr[9] == 0x00 && addr[10] == (byte) 0xFF
                && addr[11] == (byte) 0xFF;
    }

    private static boolean isULA(final byte[] addr) {
        return addr.length == 16 && ((addr[0] & 0xFE) == (byte) 0xFC);
    }

    private static boolean isLinkLocal(final byte[] addr) {
        return addr.length == 16 && (addr[0] & 0xFF) == 0xFE && (addr[1] & 0xC0) == 0x80;
    }

    private static boolean isSiteLocal(final byte[] addr) {
        return addr.length == 16 && (addr[0] & 0xFF) == 0xFE && (addr[1] & 0xC0) == 0xC0;
    }


    private static boolean isEncapsulated(final InetAddress addr) {
        if (isIpv6Address(addr)) {
            final String addrStr = addr.getHostAddress();
            // Check if the IPv6 address is in the format of "::ffff:x.x.x.x"
            if (addrStr.startsWith("::ffff:")) {
                return true;
            }
        } else if (isIpv4Address(addr)) {
            // Check if the IPv4 address is in the format of "x.x.x.x"
            final byte[] byteAddr = addr.getAddress();
            if (byteAddr[0] == 0 && byteAddr[1] == 0 && byteAddr[2] == 0 && byteAddr[3] != 0) {
                return true;
            }
        }
        return false;
    }


    private static boolean isDeprecated(final InetAddress addr) {
        if (addr instanceof Inet4Address) {
            return false;
        } else if (addr instanceof Inet6Address) {
            final Inet6Address ipv6Addr = (Inet6Address) addr;
            final byte[] addressBytes = ipv6Addr.getAddress();

            // Check if the IPv6 address is IPv4-mapped
            if (addressBytes[0] == 0 && addressBytes[1] == 0 && addressBytes[2] == 0 && addressBytes[3] == 0 &&
                    addressBytes[4] == 0 && addressBytes[5] == 0 && addressBytes[6] == 0 && addressBytes[7] == 0 &&
                    addressBytes[8] == 0 && addressBytes[9] == 0 && addressBytes[10] == (byte) 0xFF && addressBytes[11] == (byte) 0xFF) {
                return true;
            }

            // Check if the IPv6 address is a link-local address
            if ((addressBytes[0] & 0xFF) == 0xFE && (addressBytes[1] & 0xC0) == 0x80) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAddressReachable(final InetAddress address, final List<NetworkInterface> networkInterfaces) throws IOException {
        // Check if the address is a link-local, loopback, or site-local address
        if (address.isLinkLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        // Check if the address is reachable on any network interface
        for (final NetworkInterface networkInterface : networkInterfaces) {
            final Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                final InetAddress addr = addresses.nextElement();
                if (addr.equals(address)) {
                    return true;
                }
            }
        }

        return false;
    }


    private static int getLabel(final InetAddress addr) {
        if (isIpv4Address(addr)) {
            return 4;
        } else if (isIpv6Address(addr)) {
            if (addr.isLoopbackAddress()) {
                return 0;
            } else if (isIpv6Address6To4(addr)) {
                return 2;
            } else if (isIpv6AddressTeredo(addr)) {
                return 5;
            } else if (isIpv6AddressULA(addr)) {
                return 13;
            } else if (((Inet6Address) addr).isIPv4CompatibleAddress()) {
                return 3;
            } else if (addr.isSiteLocalAddress()) {
                return 11;
            } else if (isIpv6Address6Bone(addr)) {
                return 12;
            } else {
                // All other IPv6 addresses, including global unicast addresses.
                return 1;
            }
        } else {
            // This should never happen.
            return 1;
        }
    }

    private static boolean isIpv6Address(final InetAddress addr) {
        return addr instanceof Inet6Address;
    }

    private static boolean isIpv4Address(final InetAddress addr) {
        return addr instanceof Inet4Address;
    }

    private static boolean isIpv6Address6To4(final InetAddress addr) {
        if (!isIpv6Address(addr)) {
            return false;
        }
        final byte[] byteAddr = addr.getAddress();
        return byteAddr[0] == 0x20 && byteAddr[1] == 0x02;
    }

    private static boolean isIpv6AddressTeredo(final InetAddress addr) {
        if (!isIpv6Address(addr)) {
            return false;
        }
        final byte[] byteAddr = addr.getAddress();
        return byteAddr[0] == 0x20 && byteAddr[1] == 0x01 && byteAddr[2] == 0x00
                && byteAddr[3] == 0x00;
    }

    private static boolean isIpv6AddressULA(final InetAddress addr) {
        return isIpv6Address(addr) && (addr.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static boolean isIpv6Address6Bone(final InetAddress addr) {
        if (!isIpv6Address(addr)) {
            return false;
        }
        final byte[] byteAddr = addr.getAddress();
        return byteAddr[0] == 0x3f && byteAddr[1] == (byte) 0xfe;
    }

    private static int getIpv6MulticastScope(final InetAddress addr) {
        return !isIpv6Address(addr) ? 0 : (addr.getAddress()[1] & 0x0f);
    }


    private static int getScope(final InetAddress addr) {
        if (isIpv6Address(addr)) {
            if (addr.isMulticastAddress()) {
                return getIpv6MulticastScope(addr);
            } else if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                return 0x02;
            } else if (addr.isSiteLocalAddress()) {
                return 0x05;
            } else {
                return 0x0e;
            }
        } else if (isIpv4Address(addr)) {
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                return 0x02;
            } else {
                return 0x0e;
            }
        } else {
            return 0x01;
        }
    }

    private static boolean isHomeAddress(final InetAddress addr) {
        if (addr instanceof Inet6Address) {
            // Check if the address is a global unicast address with the
            // high-order bit of the first octet set to zero.
            final byte[] bytes = addr.getAddress();
            return (bytes[0] & 0x80) == 0x00;
        } else if (addr instanceof Inet4Address) {
            // Check if the address is a private address.
            final byte[] bytes = addr.getAddress();
            return ((bytes[0] & 0xFF) == 10)
                    || (((bytes[0] & 0xFF) == 172) && ((bytes[1] & 0xF0) == 0x10))
                    || (((bytes[0] & 0xFF) == 192) && ((bytes[1] & 0xFF) == 168));
        }
        return false;
    }

    private static boolean isCareOfAddress(final InetAddress addr) {
        if (addr instanceof Inet6Address) {
            final byte[] bytes = addr.getAddress();
            return (bytes[0] & 0xfe) == 0xfc; // IPv6 Unique Local Addresses (ULA) range
        }
        return false;
    }

    private static boolean isSourceAddress(final InetAddress addr, final InetAddress srcAddr1, final InetAddress srcAddr2) {
        // Check if the address matches either srcAddr1 or srcAddr2
        return addr.equals(srcAddr1) || addr.equals(srcAddr2);
    }

    private static int commonPrefixLen(final InetAddress addr1, final InetAddress addr2) {
        byte[] bytes1 = addr1.getAddress();
        byte[] bytes2 = addr2.getAddress();

        if (bytes2.length == 4) {
            bytes2 = Arrays.copyOf(bytes2, 16);
        }

        if (bytes1.length > 8) {
            bytes1 = Arrays.copyOf(bytes1, 8);
            bytes2 = Arrays.copyOf(bytes2, 8);
        }

        int prefixLen = 0;
        for (int i = 0; i < bytes1.length; i++) {
            int bits = 8;
            for (int j = 7; j >= 0; j--) {
                if ((bytes1[i] & (1 << j)) == (bytes2[i] & (1 << j))) {
                    prefixLen += 1;
                } else {
                    bits--;
                }
                if (bits == 0) {
                    break;
                }
            }
            if (bits != 8) {
                break;
            }
        }
        return prefixLen;
    }
}








