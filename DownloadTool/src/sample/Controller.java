package sample;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import sample.download.DownloadFile;

import sample.download.DownloadServer;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    private Button btnCreate;

    @FXML
    private TableColumn<DownloadFile,CheckBox> checklist;

    @FXML
    private TableView<DownloadFile> tabview;
    @FXML
    private TableColumn<DownloadFile, String> tab_name;
    @FXML
    private TableColumn<DownloadFile, String> tab_link;
    @FXML
    private TableColumn<DownloadFile, String> tab_status;
    @FXML
    private TableColumn<DownloadFile, String> tab_speed;
    @FXML
    private TableColumn<DownloadFile, String> tab_filesize;
    @FXML
    private TableColumn<DownloadFile, String> tab_timeleft;
    @FXML
    private TableColumn<DownloadFile, String> tab_progress;
    @FXML
    private TableColumn<DownloadFile, Integer> tab_threadcount;

    @FXML
    private TableColumn<DownloadFile, String> tab_spendtime;

    //用于保存数据，<>中为上面Model类的类名
    private ObservableList<DownloadFile> fileData = FXCollections.observableArrayList();

    private boolean chooseAllStatus=true;



    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        checklist.setCellValueFactory(fileData->fileData.getValue().cb.getCheckBox());
        tab_name.setCellValueFactory(new PropertyValueFactory<>("name"));
        tab_filesize.setCellValueFactory(new PropertyValueFactory<>("fileSizeShow"));
        tab_link.setCellValueFactory(new PropertyValueFactory<>("link"));
        tab_speed.setCellValueFactory(new PropertyValueFactory<>("speed"));
        tab_status.setCellValueFactory(new PropertyValueFactory<>("status"));
        tab_threadcount.setCellValueFactory(new PropertyValueFactory<>("threadCountActived"));
        tab_timeleft.setCellValueFactory(new PropertyValueFactory<>("timeLeft"));
        tab_progress.setCellValueFactory(new PropertyValueFactory<>("progress"));
        tab_spendtime.setCellValueFactory(new PropertyValueFactory<>("timeSpend"));

        //绑定数据到TableView
        tabview.setItems(fileData);



        //添加数据到personData，TableView会自动更新
        //fileData.add(new DownloadFile("a.mp3","http:aa/a.mp3",1234567L,4));

        loadData();

    }


    private void loadData(){
        fileData.addAll(DownloadServer.getAllDownloadFile());
    }



    /**
     * 创建下载按钮点击
     * @param actionEvent
     */
    public void createDownload(ActionEvent actionEvent) {

        // Create the custom dialog.
        Dialog<InputResult> dialog = new Dialog<>();
        dialog.setTitle("创建");
        dialog.setHeaderText("请输入下载信息");

        // Set the icon (must be included in the project).
        ImageView imageView = new ImageView(this.getClass().getResource("/pic/down.png").toString());
        imageView.setFitHeight(50);
        imageView.setFitWidth(50);
        dialog.setGraphic(imageView);

        // Set the button types.
        ButtonType confirmBtn = new ButtonType("开始下载", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmBtn, ButtonType.CANCEL);

        // Create the fileLink,savePath,threadCount labels and fields.
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField fileLink = new TextField();
        fileLink.setPromptText("文件链接");
        TextField savePath = new TextField();
        savePath.setPromptText("保存路径");
        TextField threadCount = new TextField();
        threadCount.setPromptText("线程数");
        threadCount.setText("4");


        //选择路径
        Button btnChooser=new Button("选择路径");
        btnChooser.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                DirectoryChooser directoryChooser=new DirectoryChooser();
                File file = directoryChooser.showDialog(new Stage());
                savePath.setText( file.getPath());//选择的文件夹路径
            }
        });

        //显示图标
        ImageView iv_add = new ImageView(this.getClass().getResource("/pic/add.png").toString());
        imageView.setFitHeight(50);
        imageView.setFitWidth(50);
        ImageView iv_minus = new ImageView(this.getClass().getResource("/pic/minus.png").toString());
        imageView.setFitHeight(50);
        imageView.setFitWidth(50);
        //btnAdd.setGraphic(new ImageView(this.getClass().getResource("/pic/down.png").toString()));

        grid.add(new Label("文件链接:"), 0, 0);
        grid.add(fileLink, 1, 0);
        grid.add(new Label("保存路径:"), 0, 1);
        grid.add(savePath, 1, 1);
        grid.add(btnChooser,2,1);
        grid.add(new Label("线程数:"), 0, 3);
        grid.add(threadCount,1,3);




        //文件存储路径填写后才能开始下载
        Node confirmNode = dialog.getDialogPane().lookupButton(confirmBtn);
        confirmNode.setDisable(true);
        fileLink.textProperty().addListener((observable, oldValue, newValue) -> {
            confirmNode.setDisable(newValue.trim().isEmpty());
        });

        dialog.getDialogPane().setContent(grid);

        // Request focus on the fileLink field by default.
        Platform.runLater(() -> fileLink.requestFocus());

        // Convert the result to a fileLink-savePath-pair when the login button is clicked.
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == confirmBtn) {
                int count=4;
                try{
                     count=Integer.parseInt(threadCount.getText());
                }catch (NumberFormatException e){
                    return null;
                }

                return new InputResult(fileLink.getText(),savePath.getText(),count) ;
            }
            return null;
        });


        Optional<InputResult> result = dialog.showAndWait();

        result.ifPresent((InputResult inputResult) -> {
            System.out.println("url=" + inputResult.url + ", savePath=" + inputResult.savePath+", threadCount="+inputResult.threadCount);
            for (DownloadFile file :fileData) {
                if(file.getLink().equals(inputResult.url)){
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.titleProperty().set("信息");
                    alert.headerTextProperty().set("该下载任务已存在");
                    alert.showAndWait();
                    return;
                }
            }
            DownloadFile downloadFile=DownloadServer.getDownloadFile(inputResult.url,inputResult.savePath,inputResult.threadCount);
            fileData.add(downloadFile);
            downloadFile.onStart();
        });

    }

    /**
     * 暂停下载
     * @param actionEvent
     */
    public void pauseDownload(ActionEvent actionEvent){
        for (DownloadFile file :fileData) {
            if(file.cb.isSelected()){
                file.onPause();
            }
        }
    }

    /**
     * 继续下载
     * @param actionEvent
     */
    public void resumeDownload(ActionEvent actionEvent){
        for (DownloadFile file :fileData) {
            if(file.cb.isSelected()){
                file.onResume();
            }
        }
    }

    /**
     * 删除下载文件
     * @param actionEvent
     */
    public void deleteDownload(ActionEvent actionEvent){
        for(int i=fileData.size()-1;i>=0;i--){
            DownloadFile file=fileData.get(i);
            if(file.cb.isSelected()){
                file.onDelete();
                fileData.remove(file);
            }
        }
    }

    public void clickListView(){
        System.out.println("you clicked");
    }

    /**
     * 打开下载目录
     * @param actionEvent
     */
    public void openDir(ActionEvent actionEvent) {
        for (DownloadFile file :fileData) {
            if(file.cb.isSelected()){
                Desktop desktop=Desktop.getDesktop();
                try {
                    desktop.open(new File(file.savePath));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void chooseAll(ActionEvent actionEvent) {
        for (DownloadFile file :fileData) {
            file.cb.setSelected(chooseAllStatus);
        }
        chooseAllStatus=!chooseAllStatus;
    }


    class InputResult{
        public String url,savePath;
        public int threadCount;

        public InputResult(String url, String savePath, int threadCount) {
            this.url = url;
            this.savePath = savePath;
            this.threadCount = threadCount;
        }
    }





}
