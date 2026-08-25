package demo.expressions;

class ExpressionShowcase {
    interface Mapper {
        int apply(int value);
    }

    int eval(Object input) {
        int[] data = new int[]{1, 2, 3};
        int[][] matrix = new int[2][3];
        matrix[0][1] = data[1] + 4;

        Mapper mapper = value -> value * 2;
        java.util.function.IntUnaryOperator op = Math::abs;
        Object ref = this::hashCode;

        int shifted = (matrix[0][1] << 1) >>> 1;
        int unary = ~(-shifted);
        boolean check = input instanceof String text && !text.isBlank();
        String message = check ? textOrDefault(input) : String.valueOf(input);

        Number boxed = (Number) Integer.valueOf(op.applyAsInt(mapper.apply(unary)));

        Object created = new java.util.ArrayList<String>();
        created = ((java.util.List<?>) created).isEmpty() ? created : new Object();

        return switch (message.length()) {
            case 0 -> 0;
            case 1 -> boxed.intValue();
            default -> data[0] + data[1] + data[2];
        };
    }

    private String textOrDefault(Object value) {
        return value == null ? "null" : value.toString();
    }
}
