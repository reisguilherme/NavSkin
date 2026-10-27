# NavSkin no iPhone usando Windows

O caminho desta entrega é **um amigo compilar o NavSkin 0.5.0 no Mac e enviar o IPA ao Guilherme**. Depois, o AltServer no Windows usa a Apple Account do próprio Guilherme para assinar e instalar no iPhone com iOS 17 ou posterior. Para essa instalação, você precisa do IPA, PC, cabo USB e Apple Account.

As instruções de compilação estão no [README para quem vai usar o Mac](../README.md#para-quem-vai-compilar-no-mac). O projeto está preparado; a compilação Apple ainda precisa ser executada. O GitHub Actions permanece uma alternativa manual, mas a execução nesta conta foi bloqueada pelo GitHub. Isso não impede o amigo de compilar localmente.

## 1. Receber o IPA do Mac

No Mac, dentro da pasta que contém `ios`, `scripts` e `tests`, o amigo executa:

```bash
RUN_APPLE_TESTS=true bash scripts/build-ios-cloud.sh
```

Apesar do nome, o script roda localmente com Xcode, sem GitHub e sem login Apple. Depois de compilar e testar com sucesso, ele gera `ios/artifacts/cloud/NavSkin-unsigned.ipa` e `NavSkin-unsigned.json`. O amigo envia os dois arquivos ao Guilherme. A assinatura pessoal será feita no Windows; o pacote não precisa conter credenciais nem perfis do amigo.

Com o IPA em mãos, siga para [preparar o Windows](#2-preparar-o-windows).

### Alternativa manual: gerar pelo GitHub Actions

Use esta alternativa somente se a conta puder executar GitHub Actions. O bloqueio atual dessa execução não altera o caminho local no Mac.

1. Abra [reisguilherme/NavSkin → Actions](https://github.com/reisguilherme/NavSkin/actions).
2. Selecione **NavSkin iOS - gerar IPA para Windows**.
3. Com permissão de escrita no repositório, clique em **Run workflow**, escolha a branch e mantenha `apple_tests` habilitado para executar os testes Apple no simulador. O GitHub exige que o workflow com `workflow_dispatch` exista na branch padrão para oferecer esse botão. [Instruções do GitHub](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).
4. Aguarde a conclusão. O [workflow](../../.github/workflows/ios-cloud.yml) usa `macos-26`; esse rótulo é publicado pelo GitHub para seu runner macOS. A imagem e os SDKs instalados podem mudar. [Imagens oficiais](https://github.com/actions/runner-images#available-images).
5. Após uma execução bem-sucedida, abra **Artifacts → NavSkin-unsigned-iphoneos-N**, em que `N` é o número da execução. Baixe o ZIP e extraia **`NavSkin-unsigned.ipa`**. É necessário entrar no GitHub e ter acesso de leitura para baixar os artefatos. Este workflow mantém os arquivos por três dias. [Download de artefatos](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts).

O pacote inclui também `NavSkin-unsigned.json`, com os dados do empacotamento. `NavSkin-ios-diagnostics-N` reúne logs e, quando executado, o resultado dos testes Apple. Em caso de falha, abra a etapa que falhou e esses diagnósticos. Desabilitar `apple_tests` produz uma tentativa de build sem essa suíte e não conta como aprovação dos testes.

O arquivo é compilado para dispositivo físico arm64 e ainda precisa de assinatura pessoal para instalar. O workflow não solicita senha Apple, certificado ou chave Geoapify. Não cadastre essas credenciais nos secrets do GitHub para seguir este guia.

## 2. Preparar o Windows

Instale o [AltServer pelo site oficial](https://altstore.io/). Siga o [guia do AltStore para Windows](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows) para instalar iTunes e iCloud pelos links oficiais da Apple indicados ali. O guia recomenda essas versões em vez das versões da Microsoft Store; há uma alternativa documentada para quem precisa usar iCloud da Store.

Inicie o AltServer, conecte o iPhone por USB, desbloqueie o aparelho e confirme a confiança no computador. O ícone do AltServer aparece na área de notificação do Windows, junto ao relógio; pode estar entre os ícones ocultos. Se o iPhone não for reconhecido, confira conexão, confiança e os componentes Apple conforme o [guia de solução de problemas do AltStore](https://faq.altstore.io/altstore-classic/troubleshooting-guide).

## 3. Assinar e instalar

1. Segure **Shift** e clique no ícone do AltServer na área de notificação.
2. Escolha **Sideload .ipa…**, selecione seu iPhone e abra o arquivo `NavSkin-unsigned.ipa` recebido.
3. Informe sua Apple Account no diálogo local do AltServer e conclua a autenticação solicitada. A documentação do AltStore informa que as credenciais são enviadas à Apple. [Autenticação no AltServer](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows).
4. Aguarde a assinatura e a instalação. Esse menu permite instalar o IPA diretamente, sem instalar o aplicativo AltStore primeiro. [Menu de instalação direta documentado pelo AltServer](https://faq.altstore.io/release-notes/altserver#direct-ipa-sideloading).
5. Se o iPhone pedir confiança no desenvolvedor, abra **Ajustes → Geral → VPN e Gerenciamento de Dispositivo** e confirme o perfil da sua conta; o nome do menu varia conforme o iOS. [Orientação do AltStore](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows).
6. Ative **Ajustes → Privacidade e Segurança → Modo de Desenvolvedor**, reinicie e confirme a ativação após desbloquear. A Apple descreve essa sequência para executar aplicativos instalados com assinatura de desenvolvimento. [Modo de Desenvolvedor](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device).

Use a versão atual do AltServer. Se o modo de desenvolvedor ainda não aparecer, conclua a tentativa de instalação e consulte os guias oficiais vinculados acima; o ajuste pode depender do estado de pareamento/instalação do aparelho.

## 4. Abrir o NavSkin

O mapa mundial online é o padrão e não exige uma chave de mapa. Em **Temas e mapa**, escolha GTA SA, Rosa Pop ou RDR2. A opção **Goiânia offline** usa a cartografia incluída no aplicativo. O serviço público [OpenFreeMap](https://openfreemap.org/) fornece a base mundial sem chave.

Para busca e rotas, abra **Configurações**, informe sua chave Geoapify e ative o GPS com localização precisa. A chave fica no Keychain do iPhone. O modo mundial, a busca e o recálculo precisam de internet; o GPS e os dados locais não transformam o aplicativo em um navegador totalmente offline. Trocar tema ou cobertura preserva a navegação e o acompanhamento de localização. Veja o [guia de uso](../README.md).

## 5. Renovar a assinatura pessoal

Com Personal Team gratuita, os perfis de provisionamento vencem após **sete dias**. A Apple também limita esse modo a três aplicativos por dispositivo. [Limites da Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account).

Para este caminho de instalação direta, conecte o iPhone ao mesmo PC e repita **Shift → Sideload .ipa…** com a mesma conta antes do vencimento. O AltServer documenta renovação manual a cada sete dias para aplicativos instalados diretamente. [Renovação no AltServer](https://faq.altstore.io/release-notes/altserver#direct-ipa-sideloading). Guarde o IPA localmente para reutilizá-lo; renovar a assinatura não exige recompilar o mesmo aplicativo.

Mantenha a mesma conta e o mesmo identificador ao atualizar para preservar a identidade da instalação. Evite apagar o aplicativo como parte da renovação: desinstalar pode remover seus dados locais. A assinatura para testes pessoais e a distribuição pelo TestFlight/App Store seguem fluxos diferentes; este guia cobre a instalação pessoal no seu iPhone.
