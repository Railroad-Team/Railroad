package io.github.railroad.ide;
import io.github.railroad.Railroad;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.io.InputStream;

public class Calculator extends Pane {


    //Entry Point
    //Add Keys
    public Calculator() {
        this.setSpacing(10);
        this.setPadding(new Insets(20));
        TextField textField = new TextField();
        Label output = new Label();
        Button button = new Button("Calculate!");
        getChildren().addAll(textField,button,output);
        button.setOnMouseClicked(mouseEvent -> {
            Expression i = new ExpressionBuilder(textField.getText()).build();

            output.setText(String.valueOf(i.evaluate()));
        });


    }
    @Override
    public InputStream getLogo() {
        return Railroad.getResourceAsStream("images/IDEIcons/calculator.png");
    }

    @Override
    public String getPaneName() {
        return "Calculator";
    }
}
