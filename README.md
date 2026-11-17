# Slide TD98

Apresentador Android para o **TD98 Pro sem ATV**, com navegação completa por controle remoto e sem depender da Google Play ou serviços Google. Android 5.0 ou superior, APK universal, interface em português e funcionamento offline.

## Baixar e instalar

Baixe o APK na [última versão publicada](https://github.com/emanoelb5-lgtm/Slides-/releases/latest). Copie para um pendrive, abra pelo gerenciador do projetor e autorize a instalação se o Android solicitar.

## Apresentar

- **Abrir apresentação** usa o seletor do Android; **Pendrive e pastas** oferece um navegador próprio para firmwares sem seletor funcional.
- Uma cópia privada do arquivo fica no projetor; o pendrive pode ser removido depois da importação.
- Setas direita/baixo e CH+ avançam; esquerda/cima e CH− retornam.
- OK ou Menu abre as opções: miniaturas, salto de página, modo automático, tela preta e encerramento.
- Voltar pede confirmação antes de encerrar. Teclas numéricas permitem ir diretamente a um slide.
- Há uma apresentação de teste incluída. Margens de 0%, 3%, 5% ou 8% ajudam a compensar overscan.
- Modo leve: até 960 pixels; modo normal: até 1600 pixels. Apenas a página atual e poucas páginas próximas ficam em memória.

## Formatos

| Extensões | Leitura |
| --- | --- |
| PDF | Renderização nativa do Android |
| PPTX, PPSX, POTX, PPTM, PPSM, POTM | Texto, imagens incorporadas, formas básicas, grupos e tabelas; apresentação estática |
| PPT, PPS, POT | Leitor MS-CFB/MS-PPT básico, com slides, texto, imagens PNG/JPEG e formas simples |
| ODP, OTP, FODP | Texto, imagens e formas simples; apresentação estática |
| JPG, JPEG, PNG, WEBP, BMP, GIF, SVG | Imagens; GIF estático |
| ZIP, CBZ | Slides em imagens, ordenados numericamente pelos nomes |
| TXT, MD | Texto; seções separadas por `---` ou quebra de página |

**Limites reais:** não é um motor Microsoft Office/LibreOffice completo. Fontes especiais, animações, vídeo, áudio, SmartArt, gráficos, objetos OLE e efeitos complexos podem mudar ou não aparecer. Não executa macros. Não abre arquivos com senha, Apple Keynote ou arquivos danificados. Para preservar exatamente a aparência, use PDF ou ZIP de imagens. Limite de importação: 512 MB; PPT antigo: 96 MB.

## Uso permanente e atualizações

O aplicativo não usa ativação, prazo de uso, mensalidade ou servidor de licença. A licença do código é MIT.

O **applicationId `online.b5solucoes.slide` e a mesma chave de assinatura devem ser mantidos para sempre** para instalar uma versão por cima. O certificado de lançamento tem validade de 100 anos. Só o hash público está em `signing/certificate.sha256`. A chave privada e a senha ficam no backup privado do proprietário, fora deste repositório. Nunca as adicione ao Git ou a um artefato público.

**Atualizações** consulta a última GitHub Release e seu `update.json`. O app também consulta ao abrir, no máximo a cada 12 horas, sem interromper uma aula. Ao baixar, verifica HTTPS, tamanho, SHA-256, pacote, versão crescente e a assinatura em relação ao app instalado. A instalação passa pelo instalador padrão do Android, com confirmação do usuário; em Android 8+ pode pedir permissão para instalar por esta fonte. Não há instalação silenciosa.

## Build e publicação no Actions

1. Altere o código e incremente `version.properties`.
2. O workflow `Android build and projector checks` compila, executa testes e lint e gera `slide-release-unsigned`. Um emulador Android AOSP, sem Google, verifica leitores e controle remoto.
3. Baixe o APK não assinado desse artefato. Assine com o backup privado usando `tools/sign_release.py --apk ARQUIVO --backup PASTA_PRIVADA --sdk ANDROID_SDK --build-run ID --source-commit SHA`.
4. Grave somente `distribution/Slide-TD98.apk` e `distribution/release.json` no repositório. O workflow de publicação verifica a assinatura, confere que o conteúdo é idêntico ao APK do build aprovado e publica a Release com APK, SHA256SUMS e `update.json`.

Esse processo mantém o build no Actions sem colocar a chave privada no repositório público. A assinatura é a única etapa feita fora do Actions até que o proprietário configure um cofre/segredos para assinatura automatizada. **Nunca** gere outra chave para uma atualização.

## Privacidade

Os documentos são abertos localmente. Não há login, telemetria ou envio de arquivos. A internet é usada apenas para atualizações públicas do GitHub. Nenhuma API de IA é usada em tempo de execução.

## Dependências e validação

- AndroidSVG 1.4, Apache 2.0, para SVG: https://github.com/BigBadaboom/androidsvg
- Desugaring Android/Java: Google, GPL com Classpath Exception; dependência oficial de build.
- JUnit e AndroidX Test somente nos testes.
- Renderização PDF: API do Android. Leitores de apresentação implementados diretamente em Java/Canvas.
- Um teste de PPT antigo usa arquivo gerado independentemente pelo Apache POI (apenas ferramenta de fixture, fora do aplicativo).

O teste no emulador não substitui o teste no firmware específico do TD98. O aparelho não está conectado a este ambiente.
