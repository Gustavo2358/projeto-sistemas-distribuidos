# projeto-sistemas-distribuidos

trabalho desenvolvido para a disciplina de sistemas distribuidos da UFABC. 

Segue abaixo o enunciado do projeto. Cabe destacar que o código apresenta um estilo procedural devido as limitações do próprio projeto (dados pelo professor da disciplina), e pretendo refatorar para uma futura versão utilizando POO, conceitos de Clean Architecture e 
Event-driven Architecture para garantir maior resilência, com uso de um message broker. 

## Definição do Sistema

Crie um sistema P2P que permita a transferência de arquivos de vídeo gigantes (mais de 1 GB) entre peers, intermediados por um servidor centralizado, utilizando TCP e UDP como protocolo da camada de transporte.

O sistema funcionará de forma similar (porém muito reduzida) ao sistema Napster, um dos primeiros sistemas P2P, criado por Shawn Fanning aos 18 anos.

## Visão geral do sistema

O sistema será composto por 1 servidor (com IP e porta conhecidas) e muitos peers. O peer atua tanto como provedor de informação (neste caso de arquivos) quanto como receptor deles.

Inicialmente, o servidor estará disponível para receber requisições dos peers. Quando um PeerX entra no sistema, deve comunicar ao servidor suas informações. O servidor receberá as informações e as armazenará para futuras consultas. Quando um PeerY quiser baixar um vídeo, deverá enviar para o servidor uma requisição com o nome do arquivo. O servidor procurará pelo nome e responderá ao PeerY com uma lista de peers que o contém. O PeerY receberá a lista do servidor e escolherá um dos peers da lista (vamos supor que o escolhido é o PeerZ). A seguir, o PeerY requisitará o arquivo para o PeerZ, quem poderá aceitar o pedido, enviando o arquivo, ou rejeitar o pedido. Finalmente, quando o PeerY baixar o arquivo em uma pasta, a pessoa poderá ir na pasta e visualizá-lo usando um software externo de reprodução, como VLC.

## Funcionalidades do Servidor

- Recebe e responde simultaneamente (com threads) requisições dos peers.

- Requisição JOIN: vinda de um peer que quer entrar na rede. A requisição deve conter as informações mínimas do peer (e.g., nome dos arquivos que possui), as quais devem ser armazenadas em alguma estrutura de dados no servidor. A resposta do servidor enviada ao peer será a string JOIN_OK.

- Requisição LEAVE: vinda de um peer que quer sair da rede. As informações desse peer armazenadas no servidor devem ser eliminadas. A resposta do servidor enviada ao peer será a string LEAVE_OK.

- Requisição SEARCH: vinda de um peer que quer baixar um determinado arquivo. A requisição deve conter somente o nome do arquivo com sua extensão (e.g o string Aula.mp4). A resposta do servidor enviada ao peer será uma lista vazia ou com as informações dos peers que possuem o arquivo.

- Requisição UPDATE: vinda de um peer que baixou um arquivo. A requisição deve conter o nome do arquivo baixado, o qual será inserido na estrutura de dados que mantém as informações dos peers. A resposta do servidor enviada ao peer será a string UPDATE_OK.

- Requisição ALIVE: enviada cada 30 segundos pelo servidor aos peers para saber se estão vivos ou se saíram da rede de forma abrupta (i.e., sem avisar com o LEAVE). O servidor receberá a string “ALIVE_OK” se o peer estiver vivo. Caso o servidor não receba a resposta de um determinado peer, deverá eliminar as informações deste da estrutura de dados.

- Inicialização do servidor: o servidor deve capturar inicialmente o IP. O endereço IP a ser inserido será o 127.0.0.1. Assuma esse IP quando o Peer quiser comunicar-se com o servidor. A porta default (que permitirá aos peers conectar-se com ele) será a 10098. Sobre a captura, ela se dará pelo teclado.


## Funcionalidades do Peer (assuma o ponto de vista de um PeerX)

- Recebe e responde simultaneamente (com threads) requisições do servidor e de outros peers.
- Envia por UDP uma requisição de JOIN ao servidor. Deve esperar o JOIN_OK.
- Envia por UDP uma requisição de LEAVE ao servidor. Deve esperar o LEAVE_OK.
- Envia por UDP uma requisição de UPDATE ao servidor. Deve esperar o UPDATE_OK.
- Envia por UDP uma resposta ALIVE_OK ao servidor.
- Envia por UDP uma requisição de SEARCH ao servidor. Voltará uma lista como resposta (vazia ou com informações).
- Para os envios por UDP acima, caso não receba a resposta, deverá realizar novamente a requisição, cuidando de possíveis duplicações (e.g., atrasos na resposta do servidor).
- Envia por TCP um requisição de DOWNLOAD a outro peer. Ver abaixo as possíveis respostas obtidas.

- Requisição DOWNLOAD: vinda de outro peer (PeerY), pedindo por um determinado arquivo. O PeerX deve verificar se possui o arquivo e, de forma aleatória, aceitar ou rejeitar o pedido. Se aceito o pedido, enviará por TCP o arquivo ao PeerY. Se rejeitado, enviará por TCP a string DOWNLOAD_NEGADO ao PeerY.

- Recebimento do arquivo: o arquivo deverá ser armazenado em uma pasta específica do peer.

- Requisição DOWNLOAD_NEGADO: vinda do peer que rejeitou a requisição de DOWNLOAD. O PeerX deverá realizar o mesmo pedido a outro peer da lista obtida no SEARCH. Caso não haja, realizar o pedido (depois de um determinado período de tempo) ao mesmo peer que o rejeitou.

- Inicialização do peer: o peer deve capturar inicialmente o IP, porta, e a pasta onde estão (e serão) armazenados seus arquivos. Sobre a captura, ela se dará pelo teclado. Sobre as pastas, cada peer terá sua própria. Por exemplo, se houverem 3 peers, haverá 3 pastas diferentes. Ver o JOIN no item ‘Menu interativo’ na seção 6.

## Mensagens (prints) apresentadas na console


- Na console do peer deverão ser apresentadas “exatamente” (nem mais nem menos) as seguintes informações

- Quando receber o JOIN_OK, print “Sou peer [IP]:[porta] com arquivos [só nomes dos arquivos]”. Substitua a informação entre os parênteses com as reais. Por exemplo: Sou peer 127.0.0.1:8776 com arquivos aula1.mp4 aula2.mp4

- Quando receber a resposta do SEARCH, print “peers com arquivo solicitado: [IP:porta de cada peer da lista]”

- Quando receber o arquivo, print “Arquivo [só nome do arquivo] baixado com sucesso na pasta [nome da pasta]”.-

- Quando receber o DOWNLOAD_NEGADO, print “peer [IP]:[porta] negou o download, pedindo agora para o peer [IP]:[porta].

- Menu interativo (por console) que permita realizar a escolha somente das funções JOIN, SEARCH, DOWNLOAD e LEAVE.
  - No caso do JOIN, deve capturar só o IP, porta e a pasta onde se encontram os arquivos (e.g., c:\temp\peer1\, c:\temp\peer2\, etc.). A partir da pasta, seu código procurará nela o nome dos arquivos a serem enviados ao servidor.
  - No caso do SEARCH, deve capturar só o nome do arquivo com sua extensão (e.g., aula.mp4). A busca por ele será exatamente por esse nome. Note que não deve capturar a pasta. 
  - No caso do DOWNLOAD, deve capturar só o IP e porta do peer onde se encontra o arquivo a ser baixado. Note que não deve capturar a pasta.

- Na console do servidor deverão ser apresentadas “exatamente” (nem mais nem menos) as seguintes informações
  - Quando receber o JOIN, print “Peer [IP]:[porta] adicionado com arquivos [só nomes dos arquivos].
  - Quando receber o SEARCH, print “Peer [IP]:[porta] solicitou arquivo [só nome do arquivo].
  - Se não receber o ALIVE_OK, print “Peer [IP]:[porta] morto. Eliminando seus arquivos [só nome dos arquivos]”.
  
 ## Código fonte

- Deverá criar somente as classes Servidor, Peer e Mensagem. A última deverá ser utilizada obrigatoriamente para o envio e recebimento de informações (nas requisições e respostas). Caso envie novas classes, serão descontados 3 pontos da nota final. A única exceção é a criação das classes para Threads, mas elas deverão ser criadas dentro da classe Peer ou Servidor (e.g, classes aninhadas).

- O código fonte deverá apresentar claramente (usando comentários) os trechos de código que realizam as funcionalidades mencionadas nas Seções 4 e 5.

- O código fonte deverá ser compilado e executado por uma JDK 1.8 (também denominada JDK 8). Você poderá utilizar todas as classes e pacotes existentes nela. O link destas é [jdk-8](https://docs.oracle.com/javase/8/docs/api/)

