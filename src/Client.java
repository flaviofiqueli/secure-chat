import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import java.util.Properties;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Date;


public class Client {
    private static final String SERVER_IP = "localhost";

    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    private String name;
    private int numeroDeClientes;
    private KeyPair keyPair;
    private SecretKey sharedSecretKey;
    private boolean keysEstado = true;
    private boolean running = true;
    private SecretKey[] chavesScretas;
    private String[] nomeCliente;
    int i=0;

    public void start(String namee,int numeroDeClientes) {
        this.numeroDeClientes=numeroDeClientes;
        chavesScretas = new SecretKey[numeroDeClientes];
        nomeCliente = new String[numeroDeClientes];
        this.name=namee;
        criarInterface();
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream("project.properties"));
            int port = Integer.parseInt(properties.getProperty("port"));
            socket = new Socket(SERVER_IP, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            out.writeObject(name);
            out.flush();
            if (keysEstado) {
                try {
                    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
                    keyPairGenerator.initialize(2048);
                    keyPair = keyPairGenerator.generateKeyPair();
                    // Obter a chave pública do cliente
                    PublicKey publicKey = keyPair.getPublic();
                    out.writeObject(publicKey);
                    out.flush();

                    while(chavesScretas[numeroDeClientes-1]==null && nomeCliente[numeroDeClientes-1]==null ) {
                        PublicKey otherPublicKeys = (PublicKey) in.readObject();
                        String nomee = (String) in.readObject();
                        KeyAgreement keyAgreement = KeyAgreement.getInstance("DH");
                        keyAgreement.init(keyPair.getPrivate());
                        keyAgreement.doPhase(otherPublicKeys, true);
                        // Gerar chave secreta compartilhada com algoritmo Triple DES
                        byte[] sharedSecret = keyAgreement.generateSecret();
                        sharedSecretKey = new SecretKeySpec(sharedSecret, 0, 24, "DESede");
                        System.out.println("Chave secreta compartilhada gerada com outro cliente.");

                        for (int k = 0; k < numeroDeClientes; k++) {
                            // Verificar se a posição do array está vazia
                            if (chavesScretas[k] == null && nomeCliente[k] == null) {
                                // Se estiver vazia, armazenar a chave pública nesta posição
                                nomeCliente[k] = nomee;
                                chavesScretas[k] = sharedSecretKey; // substitua chavePublica pela chave pública que você deseja armazenar
                                break; // Sair do loop após armazenar a chave pública
                            }
                        }
                        keysEstado=false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Display a message when the client joins the chat
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    mensagensRecebidas.append(getTimeStamp()+" "+  name + " joined the chat.\n");
                }
            });

            // Loop para receber mensagens do servidor enquanto o cliente estiver em execução
            while(running) {
                try {
                    // Thread para receber mensagens do servidor
                    String message = (String) in.readObject();
                    while (message != null) {
                        String[] partes = message.split(":", 2);
                        String cliente = partes[0];
                        String mensagem = partes[1];
                        if (mensagem.equals("@exit")) {
                            // Se a mensagem indicar que o cliente saiu, exibir uma mensagem na interface do cliente
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    mensagensRecebidas.append(getTimeStamp()+" "+cliente + " saiu do chat.\n");
                                }
                            });
                        } else {
                            // Se for uma mensagem normal, processá-la normalmente
                            receberMensagem(cliente, mensagem);
                        }
                        message=null;
                    }
                } catch (SocketException se) {
                    // Socket fechado, o cliente saiu
                    System.out.println("O cliente saiu do chat.");
                    mensagensRecebidas.append(getTimeStamp()+" "+ name + " saiu do chat.\n");
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException | NoSuchPaddingException | IllegalBlockSizeException |
                 NoSuchAlgorithmException | BadPaddingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private JFrame clienteFrame;
    private JTextArea mensagemEnviar;
    private JTextArea mensagensRecebidas;

    private void criarInterface() {
        // Criação da janela para o cliente
        clienteFrame = new JFrame("Interface do " + this.name);
        clienteFrame.setSize(400, 300);
        clienteFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Layout
        JPanel painel = new JPanel();
        painel.setLayout(new BorderLayout());

        // Área para exibir mensagens recebidas
        mensagensRecebidas = new JTextArea(10, 30);
        mensagensRecebidas.setEditable(false);
        JScrollPane scrollRecebidas = new JScrollPane(mensagensRecebidas);
        painel.add(scrollRecebidas, BorderLayout.NORTH);

        // Área para escrever mensagens
        mensagemEnviar = new JTextArea(5, 30);
        JScrollPane scrollEnviar = new JScrollPane(mensagemEnviar);
        painel.add(scrollEnviar, BorderLayout.CENTER);

        // Botão enviar
        JButton botaoEnviar = new JButton("Enviar");

        // Adicionando manipulador de eventos para o fechamento da janela
        clienteFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        clienteFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                sairDoChat();
            }
        });
        botaoEnviar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                enviarMensagem();
            }
        });
        painel.add(botaoEnviar, BorderLayout.SOUTH);

        clienteFrame.add(painel);

        // Exibindo a janela
        clienteFrame.setVisible(true);
    }

    private void enviarMensagem() {
        // Loop para enviar mensagens para o servidor

        String mensagem = mensagemEnviar.getText();
        if (!mensagem.isEmpty()) {
            try {
                mensagemEnviar.setText(""); // Limpar a área de texto após o envio



                mensagensRecebidas.append(getTimeStamp() + "Tu: " + mensagem + "\n");
                if (mensagem.equals("@exit")) {
                    out.writeObject(mensagem);
                    out.flush();
                    socket.close();
                    mensagensRecebidas.append(getTimeStamp()+  nomeCliente+"LEFT CHAT");
                    return;
                }
                if (mensagem.startsWith("@")) {
                    // Extrair os nomes dos destinatários da mensagem
                    int spaceIndex = mensagem.indexOf(" ");
                    if (spaceIndex != -1) {
                        String recipientsString = mensagem.substring(1, spaceIndex);
                        String messageContent = mensagem.substring(spaceIndex + 1);
                        List<String> recipientNames = Arrays.asList(recipientsString.split(","));
                        mensagem = this.name + ": " + messageContent;
                        byte[] mensagemBytes = mensagem.getBytes();
                        for (int j = 0; j < numeroDeClientes; j++) {
                            for (int k = 0; k < recipientNames.size(); k++) {
                                if (Objects.equals(nomeCliente[j], recipientNames.get(k))) {
                                    sharedSecretKey = chavesScretas[j];
                                    byte[] ciphertext = encryptWithSecretKey(mensagemBytes, sharedSecretKey);
                                    String ciphertextString = ciphertextToString(ciphertext);
                                    String ClienteMSG = nomeCliente[j] + ":" + ciphertextString;
                                    out.writeObject(ClienteMSG);
                                    out.flush();
                                }
                            }
                        }
                    } else {
                        System.err.println("Invalid format for private message: " + mensagem);
                    }
                } else {
                    mensagem = this.name + ": " + mensagem;
                    byte[] mensagemBytes = mensagem.getBytes();
                    for (int j = 0; j < numeroDeClientes; j++) {
                        sharedSecretKey = chavesScretas[j];
                        // Criptografe a mensagem usando a chave secreta compartilhada
                        byte[] ciphertext = encryptWithSecretKey(mensagemBytes, sharedSecretKey);
                        String ciphertextString = ciphertextToString(ciphertext);
                        String ClienteMSG = nomeCliente[j] + ":" + ciphertextString;
                        out.writeObject(ClienteMSG);
                        out.flush();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException | InvalidKeyException | BadPaddingException | NoSuchAlgorithmException |
                     IllegalBlockSizeException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Método para receber mensagem ( pode chamar esse método quando receber uma mensagem)
    // Adicione este método à classe Client
    private void clienteSaiu(String cliente) {
        mensagensRecebidas.append(getTimeStamp() + cliente + " saiu do chat.\n");
    }

    // Modifique o método receberMensagem para lidar com mensagens indicando que um cliente saiu
    private void receberMensagem(String cliente, String mensagem) throws NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        if (mensagem.equals(cliente + " saiu do chat")) {
            clienteSaiu(cliente);
        } else {
            for (int j = 0; j < numeroDeClientes; j++) {
                if (Objects.equals(nomeCliente[j], cliente)) {
                    sharedSecretKey = chavesScretas[j];
                }
            }
            byte[] ciphertextReceived = stringToCiphertext(mensagem);
            // Descriptografe o texto cifrado recebido usando a chave secreta compartilhada
            byte[] decryptedBytes = decryptWithSecretKey(ciphertextReceived, sharedSecretKey);
            // Converta os bytes descriptografados de volta para uma string
            String mensagemDecifrada = new String(decryptedBytes);
            mensagensRecebidas.append(getTimeStamp() + " " + mensagemDecifrada + "\n");
        }
    }

    // Função para descriptografar usando a chave secreta
    public byte[] decryptWithSecretKey(byte[] ciphertext, SecretKey secretKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException {
        // Criar um objeto do tipo Cipher
        Cipher cipher = Cipher.getInstance("DESede");
        // Inicializar o Cipher para operação de descriptografia usando a chave secreta
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        // Descriptografar o texto cifrado
        return cipher.doFinal(ciphertext);
    }

    // Função para criptografar usando a chave secreta
    public byte[] encryptWithSecretKey(byte[] plaintext, SecretKey secretKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        // Criar um objeto do tipo Cipher
        Cipher cipher = Cipher.getInstance("DESede");
        // Inicializar o Cipher para operação de criptografia usando a chave secreta
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        // Criptografar o texto plano
        return cipher.doFinal(plaintext);
    }

    // Função para converter ciphertext para string usando Base64
    public String ciphertextToString(byte[] ciphertext) {
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    // Função para converter string para ciphertext usando Base64
    public byte[] stringToCiphertext(String ciphertextString) {
        return Base64.getDecoder().decode(ciphertextString);
    }
    private String getTimeStamp() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("[HH:mm:ss]");
        return dateFormat.format(new Date());
    }


    private void sairDoChat() {
        try {
            // Enviar uma mensagem especial para informar ao servidor que o cliente está saindo
            String mensagemSaida = "@exit"; // Mensagem especial para indicar saída
            out.writeObject(mensagemSaida);
            out.flush();
            mensagensRecebidas.append(getTimeStamp() + nomeCliente+ "Saiu" + "\n");
            // Fechar os streams de entrada e saída
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }

            // Fechar o socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            running = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Fechar apenas a janela do cliente atual
        clienteFrame.dispose();
    }

}



