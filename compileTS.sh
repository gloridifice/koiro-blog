find ./src/main/typescript/ -name "*.ts" -type f >ts-files.txt
tsc @ts-files.txt --outDIr ./static/assets/js --removeComments
rm ts-files.txt