/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dismal.calculator;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Locale;

public class ProgrammerExpressionEvaluator {

    private static final String OP_NEGATE = "NEG";

    private final CalculatorExpressionTokenizer mTokenizer;

    public ProgrammerExpressionEvaluator(CalculatorExpressionTokenizer tokenizer) {
        mTokenizer = tokenizer;
    }

    public void evaluate(CharSequence expr, int defaultBase,
            CalculatorExpressionEvaluator.EvaluateCallback callback) {
        final String normalized = mTokenizer.getNormalizedExpression(expr.toString());
        final String trimmed = trimTrailingOperators(normalized);

        if (trimmed.isEmpty()) {
            callback.onEvaluate(trimmed, 0.0, null, Calculator.INVALID_RES_ID);
            return;
        }

        try {
            final BigInteger value = evaluateToBigInteger(trimmed, defaultBase);
            callback.onEvaluate(trimmed, value.doubleValue(), format(value, defaultBase),
                    Calculator.INVALID_RES_ID);
        } catch (ArithmeticException e) {
            callback.onEvaluate(trimmed, Double.NaN, null, R.string.error_nan);
        } catch (IllegalArgumentException e) {
            callback.onEvaluate(trimmed, Double.NaN, null, R.string.error_syntax);
        }
    }

    private static String trimTrailingOperators(String expr) {
        // Remove any trailing operators so intermediate input can still show a result.
        int end = expr.length();
        while (end > 0) {
            final char c = expr.charAt(end - 1);
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '&' || c == '|' || c == '^') {
                end--;
                continue;
            }
            if (c == '<' || c == '>') {
                // Might be the tail of << or >>. Conservatively trim any trailing < or >.
                end--;
                continue;
            }
            break;
        }
        return expr.substring(0, end);
    }

    private static final class Token {
        enum Type { NUMBER, OP, LPAREN, RPAREN }

        final Type type;
        final BigInteger number;
        final String op;

        private Token(Type type, BigInteger number, String op) {
            this.type = type;
            this.number = number;
            this.op = op;
        }

        static Token number(BigInteger value) {
            return new Token(Type.NUMBER, value, null);
        }

        static Token op(String op) {
            return new Token(Type.OP, null, op);
        }

        static Token lparen() {
            return new Token(Type.LPAREN, null, null);
        }

        static Token rparen() {
            return new Token(Type.RPAREN, null, null);
        }
    }

    private BigInteger evaluateToBigInteger(String expr, int defaultBase) {
        final ArrayList<Token> output = new ArrayList<>();
        final Deque<Token> ops = new ArrayDeque<>();

        int index = 0;
        Token prev = null;
        while (index < expr.length()) {
            final char c = expr.charAt(index);
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }

            if (c == '(') {
                ops.push(Token.lparen());
                prev = Token.lparen();
                index++;
                continue;
            }
            if (c == ')') {
                while (!ops.isEmpty() && ops.peek().type != Token.Type.LPAREN) {
                    output.add(ops.pop());
                }
                if (ops.isEmpty() || ops.peek().type != Token.Type.LPAREN) {
                    throw new IllegalArgumentException("Mismatched parens");
                }
                ops.pop(); // pop '('
                prev = Token.rparen();
                index++;
                continue;
            }

            if (isOperatorStart(c)) {
                final String op = readOperator(expr, index);
                if (op == null) {
                    throw new IllegalArgumentException("Unknown operator");
                }
                index += op.length();

                final boolean unaryMinus = "-".equals(op) && (prev == null
                        || prev.type == Token.Type.OP
                        || prev.type == Token.Type.LPAREN);
                final String actualOp = unaryMinus ? OP_NEGATE : op;

                final Token opToken = Token.op(actualOp);
                while (!ops.isEmpty() && ops.peek().type == Token.Type.OP) {
                    final String topOp = ops.peek().op;
                    final int precTop = precedence(topOp);
                    final int precNew = precedence(actualOp);
                    if (precTop > precNew || (precTop == precNew && isLeftAssociative(actualOp))) {
                        output.add(ops.pop());
                    } else {
                        break;
                    }
                }
                ops.push(opToken);
                prev = opToken;
                continue;
            }

            final ParsedNumber parsedNumber = readNumber(expr, index, defaultBase);
            if (parsedNumber == null) {
                throw new IllegalArgumentException("Unexpected character: " + c);
            }
            output.add(Token.number(parsedNumber.value));
            prev = Token.number(parsedNumber.value);
            index = parsedNumber.nextIndex;
        }

        while (!ops.isEmpty()) {
            final Token t = ops.pop();
            if (t.type == Token.Type.LPAREN) {
                throw new IllegalArgumentException("Mismatched parens");
            }
            output.add(t);
        }

        final Deque<BigInteger> stack = new ArrayDeque<>();
        for (Token t : output) {
            if (t.type == Token.Type.NUMBER) {
                stack.push(t.number);
                continue;
            }
            if (t.type != Token.Type.OP) {
                throw new IllegalArgumentException("Invalid RPN token");
            }

            if (OP_NEGATE.equals(t.op)) {
                if (stack.isEmpty()) {
                    throw new IllegalArgumentException("Missing operand");
                }
                stack.push(stack.pop().negate());
                continue;
            }

            if (stack.size() < 2) {
                throw new IllegalArgumentException("Missing operand");
            }
            final BigInteger b = stack.pop();
            final BigInteger a = stack.pop();
            stack.push(applyBinaryOp(t.op, a, b));
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException("Bad expression");
        }
        return stack.pop();
    }

    private static BigInteger applyBinaryOp(String op, BigInteger a, BigInteger b) {
        switch (op) {
            case "+": return a.add(b);
            case "-": return a.subtract(b);
            case "*": return a.multiply(b);
            case "/": return a.divide(b);
            case "&": return a.and(b);
            case "|": return a.or(b);
            case "^": return a.xor(b);
            case "<<": return a.shiftLeft(toIntExact(b));
            case ">>": return a.shiftRight(toIntExact(b));
            default: throw new IllegalArgumentException("Unknown op: " + op);
        }
    }

    private static int toIntExact(BigInteger value) {
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Shift amount too large");
        }
    }

    private static boolean isOperatorStart(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '&' || c == '|' || c == '^'
                || c == '<' || c == '>';
    }

    private static String readOperator(String expr, int index) {
        final char c = expr.charAt(index);
        if (c == '<' || c == '>') {
            if (index + 1 < expr.length() && expr.charAt(index + 1) == c) {
                return "" + c + c;
            }
            return null;
        }
        return String.valueOf(c);
    }

    private static int precedence(String op) {
        if (OP_NEGATE.equals(op)) return 7;
        if ("*".equals(op) || "/".equals(op)) return 6;
        if ("+".equals(op) || "-".equals(op)) return 5;
        if ("<<".equals(op) || ">>".equals(op)) return 4;
        if ("&".equals(op)) return 3;
        if ("^".equals(op)) return 2;
        if ("|".equals(op)) return 1;
        return 0;
    }

    private static boolean isLeftAssociative(String op) {
        return !OP_NEGATE.equals(op);
    }

    private static final class ParsedNumber {
        final BigInteger value;
        final int nextIndex;

        ParsedNumber(BigInteger value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }

    private static ParsedNumber readNumber(String expr, int index, int defaultBase) {
        int base = defaultBase;
        int i = index;

        if (i + 2 <= expr.length() && expr.charAt(i) == '0') {
            final char next = expr.charAt(i + 1);
            if (next == 'x' || next == 'X') {
                base = 16;
                i += 2;
            } else if (next == 'b' || next == 'B') {
                base = 2;
                i += 2;
            }
        }

        final int startDigits = i;
        BigInteger value = BigInteger.ZERO;
        while (i < expr.length()) {
            final int digit = digitValue(expr.charAt(i), base);
            if (digit < 0) break;
            value = value.multiply(BigInteger.valueOf(base)).add(BigInteger.valueOf(digit));
            i++;
        }

        if (i == startDigits) {
            return null;
        }
        return new ParsedNumber(value, i);
    }

    private static int digitValue(char c, int base) {
        if (c >= '0' && c <= '9') {
            final int v = c - '0';
            return v < base ? v : -1;
        }
        if (base <= 10) {
            return -1;
        }
        if (c >= 'a' && c <= 'f') {
            final int v = 10 + (c - 'a');
            return v < base ? v : -1;
        }
        if (c >= 'A' && c <= 'F') {
            final int v = 10 + (c - 'A');
            return v < base ? v : -1;
        }
        return -1;
    }

    private static String format(BigInteger value, int base) {
        switch (base) {
            case 16:
                return value.toString(16).toUpperCase(Locale.US);
            case 2:
                return value.toString(2);
            case 10:
            default:
                return value.toString(10);
        }
    }
}

