import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class Test {
    // Custom annotation
    @interface MyAnnotation {
        String value();
    }

    // Enum example
    enum Color {
        RED, GREEN, BLUE;
    }

    // Generic class
    class Box<T> {
        private T value;

        public Box(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }
    }

    // Interface example
    interface Greetable {
        void greet(String name);
    }

    // Abstract class example
    abstract class Animal {
        public abstract void sound();
    }

    // Class with inner class
    class OuterClass {
        private String message = "Hello from Outer Class";

        class InnerClass {
            public void printMessage() {
                System.out.println(message);
            }
        }

        public void createInnerClass() {
            InnerClass inner = new InnerClass();
            inner.printMessage();
        }
    }

    // Main class demonstrating various features
    @MyAnnotation("DemoClass")
    public class DemoClass extends Animal implements Greetable {
        // Static variable
        static int staticCounter = 0;

        // Instance variables
        private int id;
        private String name;
        private Color favoriteColor;
        private List<Box<Integer>> integerBoxList = new ArrayList<>();

        // Constructor
        public DemoClass(int id, String name, Color favoriteColor) {
            this.id = id;
            this.name = name;
            this.favoriteColor = favoriteColor;
        }

        // Static method
        public static void incrementCounter() {
            staticCounter++;
        }

        // Overridden method from Animal
        @Override
        public void sound() {
            System.out.println("Animal makes sound");
        }

        // Overridden method from Greetable
        @Override
        public void greet(String name) {
            System.out.println("Hello, " + name);
        }

        // Method demonstrating exception handling
        public void performDivision(int a, int b) {
            try {
                int result = a / b;
                System.out.println("Result: " + result);
            } catch (ArithmeticException e) {
                System.out.println("Division by zero is not allowed");
            }
        }

        // Method demonstrating lambda expressions and streams
        public void processNumbers(List<Integer> numbers) {
            List<Integer> squaredNumbers = numbers.stream()
                .map(n -> n * n)
                .collect(Collectors.toList());
            System.out.println("Squared Numbers: " + squaredNumbers);
        }

        // Method demonstrating inner class usage
        public void demonstrateInnerClass() {
            OuterClass outer = new OuterClass();
            outer.createInnerClass();
        }

        // Method demonstrating generics
        public void addIntegerToBox(int number) {
            Box<Integer> box = new Box<>(number);
            integerBoxList.add(box);
        }

        // Main method
        public static void main(String[] args) {
            DemoClass demo = new DemoClass(1, "Demo", Color.RED);
            demo.greet(demo.name);
            demo.sound();
            demo.performDivision(10, 2);
            demo.performDivision(10, 0);
            demo.processNumbers(Arrays.asList(1, 2, 3, 4, 5));
            demo.demonstrateInnerClass();
            demo.addIntegerToBox(10);

            incrementCounter();
            System.out.println("Static Counter: " + staticCounter);
        }
    }
}
