package athena.coder.ui.basicui;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;

public class BasicButton {

    //无边框按钮
    public static Button newNoBorderButton() {
        Button send = new Button();
        send.getStyleClass().add(Styles.FLAT);
        send.setPadding(Insets.EMPTY);           // 清除按钮内部 padding
        send.setStyle("""
                    -fx-background-insets: 0;            /* 清除背景层间距 */
                    -fx-border-insets: 0;                /* 清除边框层间距 */
                    -fx-padding: 0;                      /* CSS 层面兜底清除 padding */
                    -fx-background-radius: 4;            /* 按需调整圆角 */
                    -fx-border-radius: 4;
                """);
        send.setCursor(Cursor.HAND);
        return send;
    }
}
