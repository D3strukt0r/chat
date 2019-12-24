package com.orbitrondev.Controller;

import com.orbitrondev.Abstract.Controller;
import com.orbitrondev.Model.*;
import com.orbitrondev.View.DashboardView;
import com.orbitrondev.View.Helper;
import com.orbitrondev.View.LoginView;
import com.orbitrondev.View.RegisterView;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController extends Controller<LoginsModel, LoginView> {
    protected LoginController(LoginsModel model, LoginView view) {
        super(model, view);

        // register ourselves to listen for button clicks
        view.getBtnLogin().setOnAction(event -> clickOnLogin());

        // register ourselves to listen for button clicks
        view.getBtnRegister().setOnAction(event -> clickOnRegister());

        // register ourselves to handle window-closing event
        view.getStage().setOnCloseRequest(event -> Platform.exit());
    }

    public void disableInputs() {
        view.getUsername().setDisable(true);
        view.getPassword().setDisable(true);
    }

    public void disableAll() {
        disableInputs();
        view.getBtnLogin().setDisable(true);
        view.getBtnRegister().setDisable(true);
    }

    public void enableInputs() {
        view.getUsername().setDisable(false);
        view.getPassword().setDisable(false);
    }

    public void enableAll() {
        enableInputs();
        view.getBtnLogin().setDisable(false);
        view.getBtnRegister().setDisable(false);
    }

    public void clickOnRegister() {
        Platform.runLater(() -> {
            // Open login window and close server connection window
            Stage appStage = new Stage();
            RegisterModel model = new RegisterModel();
            RegisterView newView = new RegisterView(appStage, model);
            new RegisterController(model, newView);

            view.stop();
            view = null;
            newView.start();
        });
    }

    public void clickOnLogin() {
        // Disable everything to prevent something while working on the data
        disableAll();

        MainModel mainModel;
        if (serviceLocator.getModel() == null) {
            mainModel = new MainModel();
            serviceLocator.setModel(mainModel);
        } else {
            mainModel = serviceLocator.getModel();
        }

        LoginModel login = new LoginModel(view.getUsername().getText(), view.getPassword().getText());
        mainModel.setCurrentLogin(login);

        // Connection would freeze window (and the animations) so do it in a different thread.
        Runnable loginTask = () -> {
            try {
                // Try to login (the BackendController automatically saves it to the DB)
                serviceLocator.getBackend().sendLogin(login);
            } catch (IOException e) {
                // This exception contains ConnectException, which basically means, it couldn't connect to the server.
                enableAll();
                Platform.runLater(() -> {
                    if (view.getErrorMessage().getChildren().size() == 0) {
                        // Make window larger, so it doesn't become crammed, only if we haven't done so yet
                        view.getStage().setHeight(view.getStage().getHeight() + 30);
                    }
                    Text text = Helper.useText("gui.login.loginFailed");
                    text.setFill(Color.RED);
                    view.getErrorMessage().getChildren().clear();
                    view.getErrorMessage().getChildren().addAll(text, Helper.useSpacer(20));
                });
            }

            if (login.getToken() != null) {
                Platform.runLater(() -> {
                    // Open login window and close server connection window
                    Stage appStage = new Stage();
                    DashboardModel model = new DashboardModel();
                    DashboardView newView = new DashboardView(appStage, model);
                    new DashboardController(model, newView);

                    view.stop();
                    view = null;
                    newView.start();
                });
            } else {
                enableAll();
                Platform.runLater(() -> {
                    if (view.getErrorMessage().getChildren().size() == 0) {
                        // Make window larger, so it doesn't become crammed, only if we haven't done so yet
                        view.getStage().setHeight(view.getStage().getHeight() + 30);
                    }
                    Text text = Helper.useText("gui.login.loginFailed");
                    text.setFill(Color.RED);
                    view.getErrorMessage().getChildren().clear();
                    view.getErrorMessage().getChildren().addAll(text, Helper.useSpacer(20));
                });
            }
        };
        new Thread(loginTask).start();
    }
}