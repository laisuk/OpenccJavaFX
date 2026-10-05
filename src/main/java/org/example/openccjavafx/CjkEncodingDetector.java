package org.example.openccjavafx;/*
 * CjkEncodingDetector.java
 *
 * Pure Java CJK/Unicode encoding detector.
 *
 * Unicode/BOM/UTF-16 detection: project code.
 * Big5/GB18030 statistical fallback: adapted from uchardet 0.0.5 /
 * Mozilla Universal Charset Detector.
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file derived from Mozilla charset detector code are
 * subject to the Mozilla Public License Version 1.1 (the "License"); you may
 * not use those portions except in compliance with the License. You may obtain
 * a copy of the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the License.
 *
 * The Original Code is Mozilla charset detector code.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1998-2001
 * the Initial Developer. All Rights Reserved.
 *
 * Alternatively, the contents derived from Mozilla code may be used under the
 * terms of either the GNU General Public License Version 2 or later (the
 * "GPL"), or the GNU Lesser General Public License Version 2.1 or later
 * (the "LGPL"), in which case the provisions of the GPL or LGPL are applicable
 * instead of those above.
 *
 * ***** END LICENSE BLOCK *****
 */

import java.util.Objects;

/**
 * Detects common Unicode and CJK text encodings directly from a byte array.
 *
 * <p>The detector is deliberately independent of files, streams, GUI state,
 * and decoding policy. Callers supply bytes and decide how to map the returned
 * {@link EncodingKind} to a Java {@code Charset}.</p>
 *
 * <p>Detection is performed in this order:</p>
 * <ol>
 *     <li>UTF-8 / UTF-16 byte-order marks;</li>
 *     <li>ASCII or strict UTF-8 validation over the complete requested range;</li>
 *     <li>BOM-less UTF-16 LE/BE structural heuristics;</li>
 *     <li>Big5 versus GB18030-family statistical detection;</li>
 *     <li>{@link EncodingKind#UNKNOWN} when no result is sufficiently reliable.</li>
 * </ol>
 *
 * <p>GB2312 and GBK-family input is intentionally reported as
 * {@link EncodingKind#GB18030}, because GB18030 is the superset decoder expected
 * by callers. Statistical heuristics inspect at most 128 KiB, while strict
 * UTF-8 validation examines the entire requested range.</p>
 *
 * <p>This class is stateless and thread-safe. Detection itself does not create
 * a byte-array slice.</p>
 */
public final class CjkEncodingDetector {

    private static final int MAX_HEURISTIC_SAMPLE_SIZE = 128 * 1024;
    private static final float MIN_LEGACY_CONFIDENCE = 0.20F;
    private static final float MIN_LEGACY_MARGIN = 0.05F;

    private static final int STATE_START = 0;
    private static final int STATE_ERROR = 1;
    private static final int STATE_ITS_ME = 2;

    private CjkEncodingDetector() {
    }

    /**
     * Encoding families recognized by this detector.
     *
     * <p>BOM-bearing Unicode encodings are distinguished from their BOM-less
     * forms so callers may preserve that information if needed.</p>
     */
    public enum EncodingKind {
        UNKNOWN,
        ASCII,
        UTF8,
        UTF8_BOM,
        UTF16_LE,
        UTF16_LE_BOM,
        UTF16_BE,
        UTF16_BE_BOM,
        BIG5,
        GB18030
    }

    /**
     * Immutable result returned by {@link #detect(byte[])}.
     */
    public static final class Result {
        private final EncodingKind encoding;
        private final int bomSize;
        private final float confidence;

        private Result(EncodingKind encoding, int bomSize, float confidence) {
            this.encoding = Objects.requireNonNull(encoding, "encoding");
            this.bomSize = bomSize;
            this.confidence = confidence;
        }

        /** Returns the detected encoding, or {@link EncodingKind#UNKNOWN}. */
        public EncodingKind getEncoding() {
            return encoding;
        }

        /** Returns the detected BOM size in bytes, or {@code 0} when absent. */
        public int getBomSize() {
            return bomSize;
        }

        /**
         * Returns the detector confidence in the range {@code 0.0f..1.0f}.
         *
         * <p>Unicode/BOM and strict UTF-8 results are deterministic and use
         * {@code 1.0f}; BOM-less UTF-16 uses a heuristic confidence of
         * {@code 0.90f}; legacy Chinese results use their statistical score.</p>
         */
        public float getConfidence() {
            return confidence;
        }

        /** Returns {@code true} when an encoding was detected. */
        public boolean isDetected() {
            return encoding != EncodingKind.UNKNOWN;
        }

        /** Returns {@code true} when a Unicode BOM was detected. */
        public boolean hasBom() {
            return bomSize != 0;
        }

        /** Returns {@code true} for ASCII, UTF-8, and UTF-16 results. */
        public boolean isUnicode() {
            return switch (encoding) {
                case ASCII, UTF8, UTF8_BOM, UTF16_LE, UTF16_LE_BOM, UTF16_BE, UTF16_BE_BOM -> true;
                default -> false;
            };
        }

        /** Returns the human-readable encoding name. */
        @Override
        public String toString() {
            return getEncodingName(encoding);
        }
    }

    /**
     * Detects an encoding from the complete byte array.
     *
     * @param data bytes to inspect; must not be {@code null}
     * @return immutable detection result
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static Result detect(byte[] data) {
        Objects.requireNonNull(data, "data");
        return detect(data, 0, data.length);
    }

    /**
     * Detects an encoding from a byte-array range without allocating a slice.
     *
     * @param data bytes containing the requested range
     * @param offset first byte to inspect
     * @param count number of bytes to inspect
     * @return immutable detection result
     * @throws NullPointerException if {@code data} is {@code null}
     * @throws IndexOutOfBoundsException if the requested range is invalid
     */
    public static Result detect(byte[] data, int offset, int count) {
        Objects.requireNonNull(data, "data");
        if (offset < 0 || count < 0 || offset > data.length - count) {
            throw new IndexOutOfBoundsException(
                    "Invalid byte range: offset=" + offset + ", count=" + count
                            + ", length=" + data.length);
        }

        if (count == 0) {
            return unknown();
        }

        // 1. BOM detection.
        if (startsWith(data, offset, count, 0xEF, 0xBB, 0xBF)) {
            return new Result(EncodingKind.UTF8_BOM, 3, 1.0F);
        }
        if (startsWith(data, offset, count, 0xFF, 0xFE)) {
            return new Result(EncodingKind.UTF16_LE_BOM, 2, 1.0F);
        }
        if (startsWith(data, offset, count, 0xFE, 0xFF)) {
            return new Result(EncodingKind.UTF16_BE_BOM, 2, 1.0F);
        }

        // 2-3. Classify ASCII / strict UTF-8 in one full-range pass.
        EncodingKind utf8Kind = classifyUtf8(data, offset, count);
        if (utf8Kind != EncodingKind.UNKNOWN) {
            return new Result(utf8Kind, 0, 1.0F);
        }

        // 4. UTF-16 without BOM.
        if (looksLikeUtf16Le(data, offset, count)) {
            return new Result(EncodingKind.UTF16_LE, 0, 0.90F);
        }
        if (looksLikeUtf16Be(data, offset, count)) {
            return new Result(EncodingKind.UTF16_BE, 0, 0.90F);
        }

        // 5. Chinese legacy fallback.
        return detectChineseLegacy(data, offset, count);
    }

    /**
     * Returns a stable human-readable name for a detected encoding.
     *
     * @param encoding encoding kind
     * @return display name
     */
    public static String getEncodingName(EncodingKind encoding) {
        Objects.requireNonNull(encoding, "encoding");
        return switch (encoding) {
            case ASCII -> "ASCII";
            case UTF8 -> "UTF-8";
            case UTF8_BOM -> "UTF-8 BOM";
            case UTF16_LE -> "UTF-16 LE";
            case UTF16_LE_BOM -> "UTF-16 LE BOM";
            case UTF16_BE -> "UTF-16 BE";
            case UTF16_BE_BOM -> "UTF-16 BE BOM";
            case BIG5 -> "Big5";
            case GB18030 -> "GB18030";
            default -> "Unknown";
        };
    }

    private static Result unknown() {
        return new Result(EncodingKind.UNKNOWN, 0, 0.0F);
    }

    private static boolean startsWith(
            byte[] data, int offset, int count, int b0, int b1) {
        return count >= 2
                && unsigned(data[offset]) == b0
                && unsigned(data[offset + 1]) == b1;
    }

    private static boolean startsWith(
            byte[] data, int offset, int count, int b0, int b1, int b2) {
        return count >= 3
                && unsigned(data[offset]) == b0
                && unsigned(data[offset + 1]) == b1
                && unsigned(data[offset + 2]) == b2;
    }

    private static EncodingKind classifyUtf8(byte[] data, int offset, int count) {
        int i = offset;
        int end = offset + count;
        boolean ascii = true;

        while (i < end) {
            int c0 = unsigned(data[i]);

            if (c0 <= 0x7F) {
                i++;
                continue;
            }

            ascii = false;

            if (c0 >= 0xC2 && c0 <= 0xDF) {
                if (i + 1 >= end || !isContinuation(data[i + 1])) {
                    return EncodingKind.UNKNOWN;
                }
                i += 2;
                continue;
            }

            if (c0 >= 0xE0 && c0 <= 0xEF) {
                if (i + 2 >= end) {
                    return EncodingKind.UNKNOWN;
                }

                int c1 = unsigned(data[i + 1]);
                int c2 = unsigned(data[i + 2]);

                if ((c2 & 0xC0) != 0x80) {
                    return EncodingKind.UNKNOWN;
                }

                if (c0 == 0xE0) {
                    if (c1 < 0xA0 || c1 > 0xBF) {
                        return EncodingKind.UNKNOWN;
                    }
                } else if (c0 == 0xED) {
                    // Reject UTF-16 surrogate range U+D800 to U+DFFF.
                    if (c1 < 0x80 || c1 > 0x9F) {
                        return EncodingKind.UNKNOWN;
                    }
                } else if ((c1 & 0xC0) != 0x80) {
                    return EncodingKind.UNKNOWN;
                }

                i += 3;
                continue;
            }

            if (c0 >= 0xF0 && c0 <= 0xF4) {
                if (i + 3 >= end) {
                    return EncodingKind.UNKNOWN;
                }

                int c1 = unsigned(data[i + 1]);
                int c2 = unsigned(data[i + 2]);
                int c3 = unsigned(data[i + 3]);

                if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80) {
                    return EncodingKind.UNKNOWN;
                }

                if (c0 == 0xF0) {
                    if (c1 < 0x90 || c1 > 0xBF) {
                        return EncodingKind.UNKNOWN;
                    }
                } else if (c0 == 0xF4) {
                    if (c1 < 0x80 || c1 > 0x8F) {
                        return EncodingKind.UNKNOWN;
                    }
                } else if ((c1 & 0xC0) != 0x80) {
                    return EncodingKind.UNKNOWN;
                }

                i += 4;
                continue;
            }

            return EncodingKind.UNKNOWN;
        }

        return ascii ? EncodingKind.ASCII : EncodingKind.UTF8;
    }

    private static boolean isContinuation(byte value) {
        return (unsigned(value) & 0xC0) == 0x80;
    }

    private static boolean looksLikeUtf16Le(byte[] data, int offset, int count) {
        return looksLikeUtf16(data, offset, count, 1);
    }

    private static boolean looksLikeUtf16Be(byte[] data, int offset, int count) {
        return looksLikeUtf16(data, offset, count, 0);
    }

    private static boolean looksLikeUtf16(
            byte[] data, int offset, int count, int zeroByteIndex) {
        // Preserve the whole-range structural requirement, but bound the
        // statistical zero-byte heuristic so huge files are not rescanned.
        if (count < 4 || (count & 1) != 0) {
            return false;
        }

        int sampleCount = Math.min(count, MAX_HEURISTIC_SAMPLE_SIZE) & ~1;
        int end = offset + sampleCount;
        int zeroBytes = 0;
        int pairs = 0;

        for (int i = offset; i < end; i += 2) {
            int first = unsigned(data[i]);
            int second = unsigned(data[i + 1]);
            int zeroByte = zeroByteIndex == 0 ? first : second;
            int otherByte = zeroByteIndex == 0 ? second : first;

            if (zeroByte == 0 && otherByte != 0) {
                zeroBytes++;
            }
            pairs++;
        }

        return pairs != 0 && zeroBytes * 100 / pairs >= 60;
    }

    private static Result detectChineseLegacy(byte[] data, int offset, int count) {
        int sampleCount = Math.min(count, MAX_HEURISTIC_SAMPLE_SIZE);

        float big5Confidence = probeBig5(data, offset, sampleCount);
        float gb18030Confidence = probeGb18030(data, offset, sampleCount);

        float best = Math.max(big5Confidence, gb18030Confidence);
        float second = Math.min(big5Confidence, gb18030Confidence);

        if (best < MIN_LEGACY_CONFIDENCE
                || best - second < MIN_LEGACY_MARGIN) {
            return unknown();
        }

        return big5Confidence > gb18030Confidence
                ? new Result(EncodingKind.BIG5, 0, best)
                : new Result(EncodingKind.GB18030, 0, best);
    }

    private static float probeBig5(byte[] data, int offset, int count) {
        CodingStateMachine stateMachine = new CodingStateMachine(
                BIG5_CLASS_TABLE, 5, BIG5_STATE_TABLE, BIG5_CHAR_LEN_TABLE);

        DistributionAnalysis distribution = new DistributionAnalysis(
                BIG5_FREQUENT_BITS, 5376, 0.75F, BIG5_ORDER_GETTER);

        feedProber(data, offset, count, stateMachine, distribution);
        return distribution.getConfidence();
    }

    private static float probeGb18030(byte[] data, int offset, int count) {
        CodingStateMachine stateMachine = new CodingStateMachine(
                GB18030_CLASS_TABLE, 7, GB18030_STATE_TABLE, GB18030_CHAR_LEN_TABLE);

        DistributionAnalysis distribution = new DistributionAnalysis(
                GB2312_FREQUENT_BITS, 3760, 0.90F, GB2312_ORDER_GETTER);

        feedProber(data, offset, count, stateMachine, distribution);
        return distribution.getConfidence();
    }

    private static void feedProber(
            byte[] data,
            int offset,
            int count,
            CodingStateMachine stateMachine,
            DistributionAnalysis distribution) {
        int end = offset + count;

        for (int i = offset; i < end; i++) {
            int state = stateMachine.nextState(data[i]);

            if (state == STATE_ITS_ME) {
                break;
            }

            if (state != STATE_START) {
                continue;
            }

            int charLen = stateMachine.getCurrentCharLen();

            // The detector is fed as one contiguous sample, therefore every
            // completed 2-byte character has its lead byte at i - 1.
            if (charLen == 2 && i > offset) {
                distribution.handleOneChar(data[i - 1], data[i]);
            }
        }
    }

    private interface OrderGetter {
        int getOrder(byte first, byte second);
    }

    private static final OrderGetter GB2312_ORDER_GETTER = new OrderGetter() {
        @Override
        public int getOrder(byte first, byte second) {
            return getGb2312Order(first, second);
        }
    };

    private static final OrderGetter BIG5_ORDER_GETTER = new OrderGetter() {
        @Override
        public int getOrder(byte first, byte second) {
            return getBig5Order(first, second);
        }
    };

    private static final class DistributionAnalysis {
        private static final int MINIMUM_DATA_THRESHOLD = 4;

        private final int[] frequentBits;
        private final int tableSize;
        private final float typicalDistributionRatio;
        private final OrderGetter orderGetter;

        private int totalChars;
        private int freqChars;

        private DistributionAnalysis(
                int[] frequentBits,
                int tableSize,
                float typicalDistributionRatio,
                OrderGetter orderGetter) {
            this.frequentBits = frequentBits;
            this.tableSize = tableSize;
            this.typicalDistributionRatio = typicalDistributionRatio;
            this.orderGetter = orderGetter;
        }

        private void handleOneChar(byte first, byte second) {
            int order = orderGetter.getOrder(first, second);
            if (order < 0) {
                return;
            }

            totalChars++;

            if (order < tableSize && isFrequent(frequentBits, order)) {
                freqChars++;
            }
        }

        private float getConfidence() {
            if (totalChars <= 0 || freqChars <= MINIMUM_DATA_THRESHOLD) {
                return 0.01F;
            }

            if (totalChars == freqChars) {
                return 0.99F;
            }

            float ratio = freqChars
                    / ((totalChars - freqChars) * typicalDistributionRatio);

            return Math.min(ratio, 0.99F);
        }
    }

    private static final class CodingStateMachine {
        private final byte[] classTable;
        private final int classFactor;
        private final byte[] stateTable;
        private final byte[] charLenTable;

        private int currentState = STATE_START;
        private int currentCharLen;

        private CodingStateMachine(
                byte[] classTable,
                int classFactor,
                byte[] stateTable,
                byte[] charLenTable) {
            this.classTable = classTable;
            this.classFactor = classFactor;
            this.stateTable = stateTable;
            this.charLenTable = charLenTable;
        }

        private int getCurrentCharLen() {
            return currentCharLen;
        }

        private int nextState(byte value) {
            int byteClass = unsigned(classTable[unsigned(value)]);

            if (currentState == STATE_START) {
                currentCharLen = unsigned(charLenTable[byteClass]);
            }

            currentState = unsigned(
                    stateTable[currentState * classFactor + byteClass]);

            return currentState;
        }
    }

    private static int getGb2312Order(byte firstByte, byte secondByte) {
        int first = unsigned(firstByte);
        int second = unsigned(secondByte);

        if (first >= 0xB0 && second >= 0xA1) {
            return 94 * (first - 0xB0) + second - 0xA1;
        }

        return -1;
    }

    private static int getBig5Order(byte firstByte, byte secondByte) {
        int first = unsigned(firstByte);
        int second = unsigned(secondByte);

        if (first < 0xA4) {
            return -1;
        }

        if (second >= 0xA1) {
            return 157 * (first - 0xA4) + second - 0xA1 + 63;
        }

        return 157 * (first - 0xA4) + second - 0x40;
    }

    private static boolean isFrequent(int[] bits, int order) {
        return (bits[order >>> 5] & (1 << (order & 31))) != 0;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    // These state-machine tables are the unpacked Big5/GB18030 models
    // from uchardet 0.0.5 nsMBCSSM.cpp.

    private static final byte[] BIG5_CLASS_TABLE = {
1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1,
            4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
            4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
            4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 0,
    };

    private static final byte[] BIG5_STATE_TABLE = {
1, 0, 0, 3, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 1,
            1, 0, 0, 0, 0, 0, 0, 0,
    };

    private static final byte[] BIG5_CHAR_LEN_TABLE = {
0, 1, 1, 2, 0
    };

    private static final byte[] GB18030_CLASS_TABLE = {
1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 4,
            5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 0,
    };

    private static final byte[] GB18030_STATE_TABLE = {
1, 0, 0, 0, 0, 0, 3, 1, 1, 1, 1, 1, 1, 1, 2, 2,
            2, 2, 2, 2, 2, 1, 1, 0, 4, 1, 0, 0, 1, 1, 1, 1,
            1, 1, 5, 1, 1, 1, 2, 1, 1, 1, 0, 0, 0, 0, 0, 0,
    };

    private static final byte[] GB18030_CHAR_LEN_TABLE = {
0, 1, 1, 1, 1, 1, 2
    };

    // uchardet's distribution algorithm only asks whether a frequency
    // rank is in the top 512. Keeping one bit per table entry preserves
    // that behavior while avoiding large rank arrays in this single-file port.

    private static final int[] BIG5_FREQUENT_BITS = {
0x20BA8DE9, 0x40E91D50, 0x4B4C4826, 0x3012B810, 0xA08482CD, 0x17200A22, 0x2A264062, 0x14C0A142,
            0x04891300, 0x0F2CC002, 0x198F8400, 0x03022704, 0x00070831, 0x44180E04, 0x05A00831, 0x00011142,
            0x00000001, 0x00044000, 0x00888808, 0x01C00511, 0x80000082, 0xA0A10180, 0x07201001, 0x4074002A,
            0x40005021, 0x20400008, 0x00000100, 0xA2000102, 0x00000002, 0x04308225, 0x02000000, 0x00800010,
            0x21021640, 0x22002000, 0x04488038, 0x06090000, 0x00200200, 0x00022000, 0x00000423, 0x01804024,
            0x02001000, 0x00400082, 0x00008000, 0x8004020A, 0x00000004, 0x20280008, 0x6D104000, 0x00C08000,
            0x40008000, 0x00000000, 0x01000888, 0x00000000, 0x00A20250, 0x11010040, 0x00040000, 0x00100020,
            0x00200100, 0x20000000, 0x00000020, 0x08010800, 0x21010000, 0x00008014, 0x00802A14, 0x00001010,
            0x060000C0, 0x0108050C, 0x20801000, 0x20000080, 0x00008000, 0x00400008, 0x00839000, 0x01010180,
            0x00000204, 0x00200000, 0x00018412, 0x001410D4, 0x20800003, 0x00810002, 0x40000240, 0x00000100,
            0x00020000, 0x0000A008, 0x00000000, 0x06000000, 0x10040000, 0x20010100, 0x00000010, 0x00000048,
            0x12040008, 0x08080000, 0xA1000280, 0x00008000, 0x00000010, 0x03A00000, 0x04000000, 0x00000028,
            0x01001000, 0x00040000, 0x02000000, 0x00200810, 0x08000020, 0x40A86000, 0x20401000, 0x000020B8,
            0x01040000, 0x00040000, 0x00026000, 0x00004200, 0x00000000, 0x20040000, 0x00000000, 0x04100000,
            0x00010080, 0x00002C00, 0x04404000, 0x00012000, 0x00480000, 0x00040800, 0x00008000, 0x0000D800,
            0x10000000, 0x00001042, 0x00001000, 0x00000000, 0x00000400, 0x00000000, 0x08000508, 0x00000000,
            0x00000000, 0x00000000, 0x02800200, 0x00080410, 0x00000080, 0x00000000, 0x00000000, 0x01000000,
            0x12000000, 0x00000080, 0x00000020, 0x20000000, 0x00000000, 0x50020008, 0x00000000, 0x02800200,
            0x00000020, 0x00010000, 0x00000001, 0x00008000, 0x00000000, 0x00000000, 0x00000000, 0x00000210,
            0x00000080, 0x00000000, 0x00000000, 0x00080000, 0x82800020, 0x00000000, 0x00000000, 0x000A8000,
            0x00010000, 0x00010000, 0x00000000, 0x00180000, 0x00000001, 0x40800000, 0x01000010, 0x00220000,
    };

    private static final int[] GB2312_FREQUENT_BITS = {
0x008A0000, 0x01010000, 0x08000800, 0x09204021, 0x00200020, 0x20002482, 0x05C00000, 0x04000213,
            0x34200010, 0x00004001, 0x00008025, 0x20008000, 0x00000804, 0x10000424, 0x04028640, 0x23A60141,
            0x1100A000, 0x48001000, 0x08008004, 0x000800C0, 0x020A0800, 0x01414020, 0x01004084, 0x20008000,
            0x810C0601, 0x40204400, 0x814A09E1, 0x00400040, 0x00098320, 0x00004580, 0x47004000, 0x40000080,
            0x038B2000, 0x00000005, 0x4C800402, 0xA0C4004C, 0x0612640A, 0x00000904, 0x00052024, 0x81120001,
            0x286010C0, 0x00415002, 0x12010004, 0x92000005, 0x00200800, 0x08005480, 0x0080A000, 0x00081000,
            0x80005000, 0x86007000, 0x14000288, 0x00083100, 0x00100200, 0x00040000, 0x00400030, 0x00000100,
            0x43103000, 0x80010001, 0x04110400, 0x40400000, 0x000A0140, 0x40000002, 0x00000008, 0x00000000,
            0x00008000, 0x00010400, 0x00802000, 0x00000848, 0x00010002, 0x04420000, 0x04910210, 0x64800640,
            0x04400010, 0x01001800, 0x12000400, 0x4C300040, 0x56121080, 0x48062F97, 0x001000C7, 0x42800101,
            0x000020A4, 0x00000004, 0x80008074, 0x80000000, 0x10081300, 0x19022000, 0x00000C40, 0x04808080,
            0x40042001, 0x00202180, 0x04140102, 0x00410008, 0x82800208, 0x18189103, 0x00001145, 0x0008B41A,
            0x40801080, 0x00080010, 0x00003010, 0x00600140, 0x01412020, 0x20259000, 0x81082013, 0x50000208,
            0x00088284, 0x00400010, 0x021A0912, 0x00030004, 0x00002288, 0x04064000, 0x08900000, 0x248D4080,
            0x2212491E, 0x0001AA0A, 0x0A440400, 0x08402002, 0x84412030, 0x00000180,
    };
}
