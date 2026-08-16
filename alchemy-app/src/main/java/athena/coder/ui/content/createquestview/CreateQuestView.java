package athena.coder.ui.content.createquestview;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import static athena.coder.entity.BasicConstants.InPutUi.TEXTAREA_HEIGHT;
import static athena.coder.entity.BasicConstants.InPutUi.TEXTAREA_WIDTH;
import static athena.coder.ui.content.basicInputview.InputView.newInputViewWithBorder;


public class CreateQuestView {


    public static BorderPane newCreateInputViewWrapper() {
        StackPane input = newInputViewWithBorder();
        BorderPane wrapperBorder = new BorderPane();
        //   wrapperBorder.setTop(createProjectView());
        wrapperBorder.setCenter(input);
        wrapperBorder.setMaxWidth(TEXTAREA_WIDTH);
        wrapperBorder.setMaxHeight(TEXTAREA_HEIGHT);
        return wrapperBorder;
    }
}
