# Evento Perto — protótipo Android 0.1

**Dor:** em eventos lotados, a internet móvel fica indisponível e o público perde informações novas sobre o evento. Este protótipo distribui avisos curtos entre celulares próximos, sem depender de servidor remoto. Não oferece acesso à internet.

## Abrir e instalar
1. Instale Android Studio com JDK 17 e Android SDK 35 no computador.
2. Abra esta pasta como projeto e aguarde o Gradle Sync. A primeira preparação exige internet para baixar ferramentas e dependências.
3. Se o Studio solicitar configuração do Gradle local, escolha Gradle 8.11.1. Este pacote não contém Gradle Wrapper; use a instalação local ou gere o wrapper com `gradle wrapper --gradle-version 8.11.1`.
4. Conecte um Android 8+ com Google Play Services atualizado e depuração USB habilitada. Clique Run. Repita em três celulares; ou use Build > Build APK(s) e instale o APK de debug nos três.
5. Ligue Bluetooth e Wi-Fi; em Androids antigos ative também a localização do sistema. Abra o app, mantenha-o em primeiro plano, use o mesmo código de evento e autorize as permissões.
6. Toque Ativar proximidade. Compare os códigos de autenticação nos dois aparelhos antes de aceitar a conexão.

## Teste que demonstra a proposta
- A e B ativam a proximidade. C permanece desligado.
- A cria um aviso; B deve recebê-lo. Anote o horário.
- Desligue a proximidade de A. Ative C e conecte B a C, reativando a proximidade de B se necessário.
- C deve receber o aviso salvo por B sem conexão com A. O aplicativo não precisa de internet durante a troca.
- Repita sem dados móveis e sem Wi-Fi conectado à internet, mantendo os rádios ligados.
- Feche e reabra B: os avisos persistem. Reconecte os aparelhos: o mesmo ID deve aparecer somente uma vez.
- Use código de evento diferente: não deve conectar. Negue permissões: deve mostrar erro sem fechar.
- Após uma hora o aviso aparece vencido e não é retransmitido. Os relógios dos aparelhos precisam estar corretos.

## O que existe no código
- Interface nativa em português, mural, criação de aviso com setor e validade de uma hora.
- Nearby Connections P2P_CLUSTER; descoberta e anúncio, confirmação de par, envio real de bytes.
- Persistência local, identificação UUID, deduplicação, validação básica, isolamento por código de evento.
- Repasse aos pares conectados e sincronização dos avisos válidos em cada nova conexão.
- Histórico limitado aos 200 avisos mais recentes; mensagens de até 280 caracteres e pacote de até 4096 bytes.

## Limites desta entrega
**Código-fonte inicial, ainda não compilado nem validado em aparelhos físicos. Não é APK pronto.** O ambiente de criação não tem Android SDK, Gradle nem compilador Kotlin. O teste A → B → C acima precisa ser executado antes de afirmar que a comunicação funciona.

Todos podem publicar avisos de teste. Autoria NÃO é autenticada: código do evento e código de pareamento não provam vínculo com a organização. Antes do uso público, provisionar chave pública da organização, assinar avisos e validar assinatura antes de salvar/repassar. Não usar este protótipo para comunicação de emergência.

A troca só é mantida enquanto a Activity existe; manter app aberto. Não há serviço de segundo plano, notificações, confirmação de leitura, garantia de entrega, proteção contra participantes maliciosos, controle de volume ou teste de multidões. Descobertas/conexões que falham podem exigir desligar e reativar a proximidade. Envio com falha é tentado novamente no próximo encontro, sem confirmação de aplicação. Nenhum aviso é recebido à distância sem pares intermediários conectados ou encontros posteriores.

## LoRa depois
Preservar o envelope JSON (`v`, `id`, `event`, `created`, `expires`, `sector`, `text`). Extrair a camada de transporte da Activity para uma interface comum; implementar adaptador BLE/USB para um rádio e encaminhamento compatível com Meshtastic. Será necessário ajustar tamanho/fragmentação, limites de tráfego e autenticação; o JSON atual não é protocolo Meshtastic e não se conecta a rádios.

## Origem e referências
Implementação própria de protótipo com Nearby, sem copiar código Meshtastic; não é fork nem cliente compatível com Meshtastic. Nearby SDK é dependência do Google, com seus próprios termos.
- https://developers.google.com/nearby/connections/android/get-started
- https://developers.google.com/nearby/connections/android/exchange-data
- https://developers.google.com/nearby/connections/overview

## Gerar APK pelo GitHub
Envie o conteúdo desta pasta para a raiz de um repositório GitHub, incluindo `.github`. Em Actions, execute **Gerar APK de teste**. Se a compilação terminar com sucesso, baixe o artefato Evento-Perto-APK-teste. Esse fluxo foi preparado mas não executado nesta entrega.
