package goldenshadow.displayentityeditor.commands;

import goldenshadow.displayentityeditor.DisplayEntityEditor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TabComplete implements TabCompleter {



    List<String> arguments = new ArrayList<>();

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {

        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            if (DisplayEntityEditor.alternateTextInput) {
                arguments = new ArrayList<>(List.of("새로고침", "편집"));
            } else {
                arguments = new ArrayList<>(List.of("새로고침"));
            }

            for (String a : arguments) {
                if (a.toLowerCase().startsWith(args[0].toLowerCase()))
                    result.add(a);
            }
            return result;
        }
        if (args.length == 2) {
            if (DisplayEntityEditor.alternateTextInput) {
                if (args[0].equalsIgnoreCase("편집")) {
                    arguments = new ArrayList<>(Arrays.asList("이름", "표시거리", "표시높이", "표시너비", "그림자반경", "그림자농도", "텍스트불투명도", "줄너비", "배경불투명도", "배경색", "텍스트", "텍스트추가", "발광색", "블록상태"));
                }
            }
            else arguments.clear();
            for (String a : arguments) {
                if (a.toLowerCase().startsWith(args[1].toLowerCase()))
                    result.add(a);
            }
            return result;
        }


        if (args.length > 2) {
            arguments.clear();
            return arguments;
        }


        return null;
    }
}
