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
package org.apache.hc.client5.http.impl.nio;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * This class implements a comparator for {@link InetAddress} instances based on the Happy Eyeballs V2 algorithm.
 * <p>
 * The comparator is used to sort a list of IP addresses based on their reachability and preference.
 *
 * <p>
 * The Happy Eyeballs algorithm is a mechanism for reducing connection latency when connecting to IPv6-capable
 * <p>
 * servers over networks where both IPv6 and IPv4 are available. The algorithm attempts to establish connections
 * <p>
 * using IPv6 and IPv4 in parallel, and selects the first connection to complete successfully.
 *
 * <p>
 * This comparator implements the Happy Eyeballs V2 rules defined in RFC 8305. The following rules are used for
 * <p>
 * comparing two IP addresses:
 *
 * <ul>
 * <li>Rule 1: Avoid unusable destinations.</li>
 * <li>Rule 2: Prefer matching scope.</li>
 * <li>Rule 3: Avoid deprecated addresses.</li>
 * <li>Rule 4: Prefer higher precedence.</li>
 * <li>Rule 5: Prefer matching label.</li>
 * <li>Rule 6: Prefer smaller address.</li>
 * <li>Rule 7: Prefer home network.</li>
 * <li>Rule 8: Prefer public network.</li>
 * <li>Rule 9: Prefer stable privacy addresses.</li>
 * <li>Rule 10: Prefer temporary addresses.</li>
 * </ul>
 *
 * @since 5.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
class InetAddressComparator implements Comparator<InetAddress> {

    /**
     * Singleton instance of the comparator.
     */
    public static final InetAddressComparator INSTANCE = new InetAddressComparator();

    /**
     * Compares two IP addresses based on the Happy Eyeballs algorithm rules.
     * <p>
     * The method first orders the addresses based on their precedence, and then compares them based on other rules,
     * <p>
     * including avoiding unusable destinations, preferring matching scope, preferring global scope, preferring
     * <p>
     * IPv6 addresses, and preferring smaller address prefixes.
     *
     * @param addr1 the first address to be compared
     * @param addr2 the second address to be compared
     * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater
     * than the second.
     */
    @Override
    public int compare(final InetAddress addr1, final InetAddress addr2) {
        if (addr1 == null && addr2 == null) {
            return 0;
        }
        if (addr1 == null) {
            return -1;
        }
        if (addr2 == null) {
            return 1;
        }

        try {
            // Get the list of network interfaces
            final List<NetworkInterface> networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());


            // Rule 1: Avoid unusable destinations.
            final boolean add1IsReachable;
            final boolean add2IsReachable;

            // Check if address 1 is reachable on any network interface
            add1IsReachable = isAddressReachable(addr1, networkInterfaces);

            // Check if address 2 is reachable on any network interface
            add2IsReachable = isAddressReachable(addr2, networkInterfaces);

            if (add1IsReachable && !add2IsReachable) {
                return -1;
            } else if (!add1IsReachable && add2IsReachable) {
                return 1;
            }

        } catch (final Exception e) {
            return 0;
        }

        // Rule 2: Prefer matching scope.
        final InetAddress localhost;
        final InetAddress sourceAddr;
        try {
            localhost = InetAddress.getLocalHost();
            sourceAddr = InetAddress.getByName(localhost.getHostAddress());
        } catch (final UnknownHostException e) {
            return 0;
        }

        final int scope1 = getScope(addr1);
        final int scope2 = getScope(addr2);
        final int scopeSource = getScope(sourceAddr);

        if (scope1 == scope2) {
            return 0;
        } else if (scope1 == scopeSource) {
            return -1;
        } else if (scope2 == scopeSource) {
            return 1;
        }

        //Rule 3: Avoid deprecated addresses.
        final boolean add1IsDeprecated = isDeprecated(addr1);
        final boolean add2IsDeprecated = isDeprecated(addr2);

        if (add1IsDeprecated && !add2IsDeprecated) {
            return 1;
        } else if (!add1IsDeprecated && add2IsDeprecated) {
            return -1;
        }


        // Rule 4: Prefer home addresses.
        final boolean isHomeAddr11 = isHomeAddress(addr1);
        final boolean isCareOfAddr11 = isCareOfAddress(addr1);
        final boolean isHomeAddr21 = isHomeAddress(addr2);
        final boolean isCareOfAddr21 = isCareOfAddress(addr2);

        if (isHomeAddr11 && isCareOfAddr11 && !isHomeAddr21) {
            return -1;
        } else if (isHomeAddr21 && isCareOfAddr21 && !isHomeAddr11) {
            return 1;
        }

        //Rule 5: Prefer matching label.
        final int label1 = getLabel(addr1);
        final int label2 = getLabel(addr2);

        if (label1 < label2) {
            return -1;
        } else if (label1 > label2) {
            return 1;
        }

        // Rule 6: Prefer higher precedence.
        final int add1Precedence = getPrecedence(addr1);
        final int add2Precedence = getPrecedence(addr2);

        if (add1Precedence > add2Precedence) {
            return -1;
        } else if (add1Precedence < add2Precedence) {
            return 1;
        }

        // Rule 7: Prefer native transport.
        final boolean addr1Encapsulated = isEncapsulated(addr1);
        final boolean addr2Encapsulated = isEncapsulated(addr2);

        if (addr1Encapsulated && !addr2Encapsulated) {
            return 1;
        } else if (!addr1Encapsulated && addr2Encapsulated) {
            return -1;
        }


        // Rule 8: Prefer smaller scope.
        if (scope1 < scope2) {
            return -1;
        } else if (scope1 > scope2) {
            return 1;
        }

        // Rule 9: Use longest matching prefix.
        final int prefixLen1 = commonPrefixLen(addr1, addr2);
        final int prefixLen2 = commonPrefixLen(addr2, addr1);

        if (prefixLen1 > prefixLen2) {
            return -1;
        } else if (prefixLen1 < prefixLen2) {
            return 1;
        }

        // Rule 10: Otherwise, leave the order unchanged.
        return 0;
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


    /**
     * Checks whether the given IPv6 address is a deprecated address.
     *
     * @param addr the IPv6 address to check.
     * @return {@code true} if the given address is deprecated, {@code false} otherwise.
     */
    private boolean isDeprecated(final InetAddress addr) {
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

    /**
     * Determines the label to use for an IP address based on its type and properties.
     *
     * @param addr the IP address to determine the label for
     * @return an integer value representing the label to use for the IP address
     */
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

    /**
     * Returns true if the given InetAddress is an IPv6 address.
     *
     * @param addr The InetAddress to check.
     * @return True if the given InetAddress is an IPv6 address; false otherwise.
     */
    private static boolean isIpv6Address(final InetAddress addr) {
        return addr instanceof Inet6Address;
    }

    /**
     * Returns true if the given InetAddress is an IPv4 address.
     *
     * @param addr The InetAddress to check.
     * @return True if the given InetAddress is an IPv4 address; false otherwise.
     */
    private static boolean isIpv4Address(final InetAddress addr) {
        return addr instanceof Inet4Address;
    }

    /**
     * Returns true if the given InetAddress is an IPv6 6to4 address.
     *
     * @param addr The InetAddress to check.
     * @return True if the given InetAddress is an IPv6 6to4 address; false otherwise.
     */
    private static boolean isIpv6Address6To4(final InetAddress addr) {
        if (!isIpv6Address(addr)) {
            return false;
        }
        final byte[] byteAddr = addr.getAddress();
        return byteAddr[0] == 0x20 && byteAddr[1] == 0x02;
    }

    /**
     * Returns true if the given InetAddress is an IPv6 Teredo address.
     *
     * @param addr The InetAddress to check.
     * @return True if the given InetAddress is an IPv6 Teredo address; false otherwise.
     */
    private static boolean isIpv6AddressTeredo(final InetAddress addr) {
        if (!isIpv6Address(addr)) {
            return false;
        }
        final byte[] byteAddr = addr.getAddress();
        return byteAddr[0] == 0x20 && byteAddr[1] == 0x01 && byteAddr[2] == 0x00
                && byteAddr[3] == 0x00;
    }

    /**
     * Returns true if the given InetAddress is an IPv6 Unique Local Address (ULA).
     *
     * @param addr The InetAddress to check.
     * @return True if the given InetAddress is a ULA; false otherwise.
     */
    private static boolean isIpv6AddressULA(final InetAddress addr) {
        return isIpv6Address(addr) && (addr.getAddress()[0] & 0xfe) == 0xfc;
    }

    /**
     * Returns true if the given InetAddress is an IPv6 6bone address.
     *
     * @param addr The InetAddress to check.
     * @return True if the given InetAddress is an IPv6 6bone address; false otherwise.
     */
    private static boolean isIpv6Address6Bone(final InetAddress addr) {
        if (!isIpv6Address(addr)) {
            return false;
        }
        final byte[] byteAddr = addr.getAddress();
        return byteAddr[0] == 0x3f && byteAddr[1] == (byte) 0xfe;
    }

    /**
     * Returns the scope of the given IPv6 multicast address.
     *
     * @param addr The IPv6 multicast address.
     * @return The scope of the given IPv6 multicast address.
     */
    private static int getIpv6MulticastScope(final InetAddress addr) {
        return !isIpv6Address(addr) ? 0 : (addr.getAddress()[1] & 0x0f);
    }

    /**
     * Determines the precedence of an IP address based on its type and scope.
     * Precedence is used to determine which of two candidate IP addresses
     * should be used as the destination address in an IP packet, according to
     * the rules defined in RFC 6724.
     *
     * @param addr The IP address to determine the precedence of.
     * @return The precedence of the IP address, as an integer. The possible
     * values are:
     * <ul>
     * <li>1 - multicast address</li>
     * <li>2 - IPv4 address</li>
     * <li>3 - global unicast address</li>
     * <li>4 - IPv4-mapped IPv6 address</li>
     * <li>5 - unique local address</li>
     * <li>6 - link-local address</li>
     * <li>7 - site-local address</li>
     * </ul>
     */
    private int getPrecedence(final InetAddress addr) {
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

    /**
     * Checks whether the given byte array represents an IPv4-mapped IPv6 address.
     *
     * @param addr the byte array to check
     * @return true if the byte array represents an IPv4-mapped IPv6 address, false otherwise
     */
    private static boolean isIPv4MappedIPv6Address(final byte[] addr) {
        return addr.length == 16 && addr[0] == 0x00 && addr[1] == 0x00 && addr[2] == 0x00
                && addr[3] == 0x00 && addr[4] == 0x00 && addr[5] == 0x00 && addr[6] == 0x00
                && addr[7] == 0x00 && addr[8] == 0x00 && addr[9] == 0x00 && addr[10] == (byte) 0xFF
                && addr[11] == (byte) 0xFF;
    }

    /**
     * Returns true if the given byte array represents an IPv6 Unique Local Address (ULA) range, false otherwise.
     *
     * @param addr the byte array to check
     * @return true if the byte array represents a ULA range, false otherwise
     */
    boolean isULA(final byte[] addr) {
        return addr.length == 16 && ((addr[0] & 0xFE) == (byte) 0xFC);
    }

    /**
     * Returns true if the given byte array represents an IPv6 link-local address, false otherwise.
     *
     * @param addr the byte array to check
     * @return true if the byte array represents a link-local address, false otherwise
     */
    private boolean isLinkLocal(final byte[] addr) {
        return addr.length == 16 && (addr[0] & 0xFF) == 0xFE && (addr[1] & 0xC0) == 0x80;
    }

    /**
     * Returns true if the given byte array represents an IPv6 site-local address, false otherwise.
     *
     * @param addr the byte array to check
     * @return true if the byte array represents a site-local address, false otherwise
     */
    private boolean isSiteLocal(final byte[] addr) {
        return addr.length == 16 && (addr[0] & 0xFF) == 0xFE && (addr[1] & 0xC0) == 0xC0;
    }


    /**
     * Checks if the given address is reachable on any network interface or if it is a link-local, loopback,
     * or site-local address.
     *
     * @param address           the address to check
     * @param networkInterfaces a list of network interfaces to check
     * @return {@code true} if the address is reachable or a link-local, loopback, or site-local address;
     * {@code false} otherwise
     */
    private static boolean isAddressReachable(final InetAddress address, final List<NetworkInterface> networkInterfaces) {
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

    /**
     * Checks if the given address is a "home" address. A home address is defined as a global unicast IPv6 address
     * with the high-order bit of the first octet set to zero, or a private IPv4 address (i.e., an address in
     * the ranges 10.0.0.0/8, 172.16.0.0/12, or 192.168.0.0/16).
     *
     * @param addr the address to check
     * @return {@code true} if the address is a "home" address; {@code false} otherwise
     */
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

    /**
     * Determines if the given InetAddress is a care-of address. A care-of address is an
     * IPv6 address that is assigned to a mobile node while it is away from its home network.
     *
     * @param addr The InetAddress to check.
     * @return True if the InetAddress is a care-of address, false otherwise.
     */
    private static boolean isCareOfAddress(final InetAddress addr) {
        if (addr instanceof Inet6Address) {
            final byte[] bytes = addr.getAddress();
            return (bytes[0] & 0xfe) == 0xfc; // IPv6 Unique Local Addresses (ULA) range
        }
        return false;
    }

    /**
     * Determines if the given InetAddress is an encapsulated address. An encapsulated address
     * is either an IPv4-mapped IPv6 address or an IPv4-compatible IPv6 address.
     *
     * @param addr The InetAddress to check.
     * @return True if the InetAddress is an encapsulated address, false otherwise.
     */
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


    /**
     * Calculates the length of the common prefix between two IP addresses.
     *
     * @param addr1 The first IP address to compare.
     * @param addr2 The second IP address to compare.
     * @return The length of the common prefix between the two IP addresses.
     */
    private static int commonPrefixLen(final InetAddress addr1, final InetAddress addr2) {
        byte[] bytes1 = addr1.getAddress();
        byte[] bytes2 = addr2.getAddress();

        // If the second address is IPv4, convert it to IPv6 format.
        if (bytes2.length == 4) {
            bytes2 = Arrays.copyOf(bytes2, 16);
        }

        // If the addresses are IPv6, truncate them to the first 64 bits.
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