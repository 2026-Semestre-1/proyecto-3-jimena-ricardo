# Ejecución

## Formato
```
java myFileSystem  # Crea un nuevo archivo de disco.
java myFileSystem miDiscoDuro.fs  # Reutiliza un archivo de disco.
```

# format
Creará el disco virtual (archivo), con un tamaño que el usuario le defina. Además, por defecto
creará un usuario root por lo tanto se solicitará su contraseña y creará su carpeta HOME.

# infoFS
Muestra la información del sistema de archivos, como tamaño, espacio de uso y disponible.

## Formato
```
username@fsname: infoFS
Nombre del FileSystem: miFS
Tamaño: 10 MB
Espacio utilizado: 3 MB
Disponible: 7 MB
```

# ls
Listar el contenido de un directorio, con la opción -R, este será recursivo.
Mostrará en la consola todos los archivos y directorios dentro de este. A nivel visual distinguir que es archivo y que son directorios.

## Formato
```
username@fsname: ls [-R]
```

# rm
Borra archivos o directorios, al usar la opción -R esto será recursivo. Hacer uso de las expresiones regulares, como: * . *.txt

## Formato
```
username@fsname: rm [-R] path
```

# Visualización gráfica del área en uso/disponible del disco
Visualizar el disco en forma gráfica, tanto su área en uso y disponible. No es un comando de terminal, sino un apartado de la GUI.

# Desfragmentar disco gráficamente
Desgragmentar el disco. No es un comando de terminal, sino un apartado de la GUI.

# useradd
Solicita el nombre completo y posteriormente le solicita la contraseña, su confirmación.

## Formato
```
username@fsname: useradd username
```

# groupadd
Por defecto se creará un grupo root, pero el usuario con privilegios podrá crear nuevos grupos.

## Formato
```
username@fsname: groupadd groupname
```

# su
Cambio de usuario. Hay dos variantes, en el primero si no se especifica el usuario se entiende que
es al root, de lo contrario se especifica. Una vez dado ENTER, este le solicita la contraseña.

## Formato
```
username@fsname: su [username]
```

# mkdir
Crea un directorio, también puede hacer en forma de lista.

## Formato
```
username@fsname: mkdir name [name2 name3 ...]
```

# mv
Renombrar o mover archivos o directorios.

## Formato
```
username@fsname: mv filename new_filename  # Renombrar
username@fsname: mv filename to_directory  # Mover
```

# cd
Cambio de directorio o salir de este.

## Formato
```
username@fsname: cd (directory | ..)
```

# whereis
Busca un archivo desde una ruta específica, puede encontrar un archivo, aunque este no sea de su propiedad.

## Formato
```
username@fsname: whereis filename
/user/path/to/filename
```

# ln
Crea un vínculo o enlace con un archivo. Muestra un mensaje de éxito si el usuario ccampos tiene permisos
sobre el archivo archive.js de lo contrario muestra un mensaje de error.

## Formato
```
username@fsname: ln filename /user/name/archive.js
Mensaje
```

# chown
Cambia el propietario un archivo o directorio. -R para recursivo.

## Formato
```
username@fsname: chown [-R] username path
 ```

# chgrp
Cambia el grupo de un archivo o directorio. -R para recursivo.

## Formato
```
username@fsname: chgrp [-R] groupname path
 ```

# Editor de texto "note"
Debe implementarse un editor de texto simple (llamado note), solo permite abrir el archivo, si
y solo si tiene los permisos necesarios y luego lo edita, al salir de este se pregunta si desea
guardar los cambios o no. Para salir del editor, será a través de una combinación de teclas

## Formato
```
username@fsname: note miArchivo.txt
```

# touch
Crea un archivo.

## Formato
```
username@fsname: touch filename
Archivo creado
```

# cat
Ve el contenido de un archivo.

## Formato
```
username@fsname: cat filename
Hola Mundo
```

# less
Ve el contenido de un archivo. Esta instrucción abre archivo y se cierra con el comando q.

## Formato
```
username@fsname: less filename
Hola Mundo este es un ejemplo de un archivo
Con varias líneas de contenido
```

# chmod
Cambia los permisos de un archivo. Cambia el usuario y grupo. El primer 7 es para el dueño y el segundo 7 para el grupo.

## Formato
```
username@fsname: chmod 77 filename
```

# exit
Cierra el programa. Sin entradas.

# passwd
Cambia la contraseña del usuario.

## Formato
```
username@fsname: passwd username
password: *******
confirm password: *******
[Mensaje de éxito o fracaso]
```

# whoami
Imprime en pantalla el usuario y nombre.

## Formato
```
username@fsname: whoami
username: ...
Full name: ...
```

# pwd
Imprime el directorio actual. Sin entradas.

# clear
Limpia la pantalla. Sin entradas.

# viewFilesOpen
Muestra la cantidad de archivos abiertos.

## Formato
```
username@fsname: viewFilesOpen
Total de archivos abiertos: 5
```

# viewFCB
Muestra la información estructural del archivo.
Muestra su nombre, dueño, fecha de creación, abierto/cerrado, tamaño, ubicación y demás atributos.

## Formato
```
username@fsname: viewFCB filename
```
