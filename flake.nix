{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-25.05";
  };

  outputs = inputs:
    let
      javaVersion = 21;

      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];

      forEachSystem = f:
        inputs.nixpkgs.lib.genAttrs systems (
          system:
          f {
            pkgs = import inputs.nixpkgs {
              inherit system;
              overlays = [ inputs.self.overlays.default ];
            };
          }
        );
    in
    {
      overlays.default = final: prev:
        let
          jdk = prev."jdk${toString javaVersion}";
          javafx = prev.openjfx;
        in
        {
          inherit jdk javafx;
          gradle = prev.gradle.override { java = jdk; };
          lombok = prev.lombok.override { inherit jdk; };
        };

      devShells = forEachSystem (
        { pkgs }:
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              gradle
              jdk
              javafx

              libGL
              gtk3
              glib
              gsettings-desktop-schemas

              xorg.libX11
              xorg.libXtst
              xorg.libXi
              xorg.libXxf86vm
            ];

            shellHook =
            let
              loadLombok = "-javaagent:${pkgs.lombok}/share/java/lombok.jar";
              prev = "\${JAVA_TOOL_OPTIONS:+ $JAVA_TOOL_OPTIONS}";
            in
            ''
              export JAVAFX_MODULE_PATH=${pkgs.javafx}/lib
              export LD_LIBRARY_PATH="${pkgs.libGL}/lib:${pkgs.gtk3}/lib:${pkgs.glib.out}/lib:${pkgs.xorg.libX11}/lib:${pkgs.xorg.libXtst}/lib:${pkgs.xorg.libXi}/lib:${pkgs.xorg.libXxf86vm}/lib:$LD_LIBRARY_PATH"
              export JAVA_TOOL_OPTIONS="${loadLombok}${prev}"
              export GSETTINGS_SCHEMA_DIR="${pkgs.gtk3}/share/gsettings-schemas/${pkgs.gtk3.name}/glib-2.0/schemas"

              mkdir ~/.config/Railroad
              touch ~/.config/Railroad/logger_config.json
            '';
          };
        }
      );
    };
}
