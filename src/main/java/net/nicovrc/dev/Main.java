package net.nicovrc.dev;

//TIP コードを<b>実行</b>するには、<shortcut actionId="Run"/> を押すか
// ガターの <icon src="AllIcons.Actions.Execute"/> アイコンをクリックします。
public class Main {
    public static void main(String[] args) {

        HTTPServer httpServer = new HTTPServer();
        try {
            httpServer.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}